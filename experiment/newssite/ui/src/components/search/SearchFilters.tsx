import React, { useState } from 'react';
import { Button } from '../common/Button';
import './SearchFilters.css';

interface SearchFiltersProps {
  formState: {
    tags: string[];
    topics: string[];
    location: string;
    after: string;
    before: string;
  };
  updateField: <K extends keyof any>(field: K, value: any) => void;
  onApply: () => void;
}

export const SearchFilters: React.FC<SearchFiltersProps> = ({
  formState,
  updateField,
  onApply,
}) => {
  const [tagInput, setTagInput] = useState('');
  const [topicInput, setTopicInput] = useState('');

  const handleAddTag = () => {
    if (tagInput.trim() && !formState.tags.includes(tagInput.trim())) {
      updateField('tags', [...formState.tags, tagInput.trim()]);
      setTagInput('');
    }
  };

  const handleRemoveTag = (tag: string) => {
    updateField('tags', formState.tags.filter(t => t !== tag));
  };

  const handleAddTopic = () => {
    if (topicInput.trim() && !formState.topics.includes(topicInput.trim())) {
      updateField('topics', [...formState.topics, topicInput.trim()]);
      setTopicInput('');
    }
  };

  const handleRemoveTopic = (topic: string) => {
    updateField('topics', formState.topics.filter(t => t !== topic));
  };

  const handleKeyPress = (e: React.KeyboardEvent, handler: () => void) => {
    if (e.key === 'Enter') {
      e.preventDefault();
      handler();
    }
  };

  return (
    <div className="search-filters">
      <div className="search-filters__header">
        <h2>Advanced Filters</h2>
      </div>

      <div className="search-filters__section">
        <label className="search-filters__label">Tags</label>
        <div className="search-filters__input-group">
          <input
            type="text"
            className="search-filters__input"
            placeholder="Add tag..."
            value={tagInput}
            onChange={(e) => setTagInput(e.target.value)}
            onKeyPress={(e) => handleKeyPress(e, handleAddTag)}
          />
          <Button
            variant="secondary"
            size="small"
            onClick={handleAddTag}
            disabled={!tagInput.trim()}
          >
            Add
          </Button>
        </div>
        {formState.tags.length > 0 && (
          <div className="search-filters__chips">
            {formState.tags.map(tag => (
              <span key={tag} className="search-filters__chip">
                {tag}
                <button
                  type="button"
                  className="search-filters__chip-remove"
                  onClick={() => handleRemoveTag(tag)}
                  aria-label={`Remove ${tag}`}
                >
                  ×
                </button>
              </span>
            ))}
          </div>
        )}
      </div>

      <div className="search-filters__section">
        <label className="search-filters__label">Topics</label>
        <div className="search-filters__input-group">
          <input
            type="text"
            className="search-filters__input"
            placeholder="Add topic..."
            value={topicInput}
            onChange={(e) => setTopicInput(e.target.value)}
            onKeyPress={(e) => handleKeyPress(e, handleAddTopic)}
          />
          <Button
            variant="secondary"
            size="small"
            onClick={handleAddTopic}
            disabled={!topicInput.trim()}
          >
            Add
          </Button>
        </div>
        {formState.topics.length > 0 && (
          <div className="search-filters__chips">
            {formState.topics.map(topic => (
              <span key={topic} className="search-filters__chip">
                {topic}
                <button
                  type="button"
                  className="search-filters__chip-remove"
                  onClick={() => handleRemoveTopic(topic)}
                  aria-label={`Remove ${topic}`}
                >
                  ×
                </button>
              </span>
            ))}
          </div>
        )}
      </div>

      <div className="search-filters__section">
        <label htmlFor="filter-location" className="search-filters__label">
          Location
        </label>
        <input
          id="filter-location"
          type="text"
          className="search-filters__input"
          placeholder="City, State or Country..."
          value={formState.location}
          onChange={(e) => updateField('location', e.target.value)}
        />
      </div>

      <div className="search-filters__section">
        <label htmlFor="filter-after" className="search-filters__label">
          After Date
        </label>
        <input
          id="filter-after"
          type="date"
          className="search-filters__input"
          value={formState.after}
          onChange={(e) => updateField('after', e.target.value)}
        />
      </div>

      <div className="search-filters__section">
        <label htmlFor="filter-before" className="search-filters__label">
          Before Date
        </label>
        <input
          id="filter-before"
          type="date"
          className="search-filters__input"
          value={formState.before}
          onChange={(e) => updateField('before', e.target.value)}
        />
      </div>

      <div className="search-filters__actions">
        <Button
          variant="primary"
          fullWidth
          onClick={onApply}
        >
          Apply Filters
        </Button>
      </div>
    </div>
  );
};