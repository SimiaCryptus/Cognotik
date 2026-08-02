// modelWorker.js — builds model + answers analysis queries off-thread

import { BidirectionalModel } from '../model/bidirectional.js';
import { getTokenizer, applyRegexConfig } from '../tokenizers/index.js';
import { analyze } from '../model/scoring.js';

let model = null;
let modelCfg = null;

self.onmessage = (e) => {
  const msg = e.data;
  try {
    if (msg.type === 'build') {
      const { corpus, config } = msg;
      const tok = getTokenizer(config.tokenizerId);
      applyRegexConfig(config);
      const tokens = tok.tokenize(corpus);
      model = new BidirectionalModel(config.order);
      modelCfg = config;
      model.build(tokens, (p) => {
        self.postMessage({ type: 'progress', phase: 'build', value: p });
      });
      self.postMessage({
        type: 'built',
        summary: model.forward.summary(config.tokenizerId, corpus.length, tokens.length),
      });
    } else if (msg.type === 'analyze') {
      if (!model) {
        self.postMessage({ type: 'error', error: 'Model not built' });
        return;
      }
      const { testText, config } = msg;
      const tok = getTokenizer(modelCfg.tokenizerId);
      applyRegexConfig(modelCfg);
      const spans = tok.tokenizeWithSpans(config.lowercase ? testText.toLowerCase() : testText);
      const results = analyze(model, spans, config, (p) => {
        self.postMessage({ type: 'progress', phase: 'analyze', value: p });
      });
      self.postMessage({ type: 'analyzed', results });
    }
  } catch (err) {
    self.postMessage({ type: 'error', error: String((err && err.message) || err) });
  }
};
