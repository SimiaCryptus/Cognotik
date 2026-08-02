/**
 * WebXR support for the Geometric Entropy Lab.
 * Renders the optimized point cloud (and optional triangulation) into an
 * immersive VR/AR session using a lightweight WebGL renderer.
 *
 * Assumes 'tf' is available globally for reading point data.
 */

export class WebXRViewer {
  constructor(getState) {
    // getState() => { points: tf.Variable, densities: Float32Array,
    //                 geometry: string, triangles: [[i,j,k],...] | null }
    this.getState = getState;
    this.session = null;
    this.refSpace = null;
    this.gl = null;
    this.canvas = null;
    this.program = null;
    this.buffers = {};
    this.mode = null; // 'immersive-vr' | 'immersive-ar'
    this.onEndCallbacks = [];
    this._frameLoop = this._frameLoop.bind(this);
  }

  static isSupported() {
    return typeof navigator !== 'undefined' && 'xr' in navigator;
  }

  async checkSessionSupport() {
    if (!WebXRViewer.isSupported()) return { vr: false, ar: false };
    let vr = false;
    let ar = false;
    try {
      vr = await navigator.xr.isSessionSupported('immersive-vr');
    } catch (e) {
      vr = false;
    }
    try {
      ar = await navigator.xr.isSessionSupported('immersive-ar');
    } catch (e) {
      ar = false;
    }
    return { vr, ar };
  }

  onEnd(cb) {
    this.onEndCallbacks.push(cb);
  }

  async start(mode = 'immersive-vr') {
    if (this.session) return;
    this.mode = mode;

    const sessionInit =
      mode === 'immersive-ar'
        ? { optionalFeatures: ['local-floor', 'bounded-floor', 'hand-tracking'] }
        : { optionalFeatures: ['local-floor', 'bounded-floor'] };

    const session = await navigator.xr.requestSession(mode, sessionInit);
    this.session = session;

    // Create offscreen WebGL context bound to the XR session.
    this.canvas = document.createElement('canvas');
    this.gl = this.canvas.getContext('webgl', {
      xrCompatible: true,
      alpha: true,
      antialias: true,
      preserveDrawingBuffer: false,
    });

    await this.gl.makeXRCompatible();

    session.updateRenderState({
      baseLayer: new XRWebGLLayer(session, this.gl),
    });

    try {
      this.refSpace = await session.requestReferenceSpace('local-floor');
    } catch (e) {
      this.refSpace = await session.requestReferenceSpace('local');
    }

    this._initGL();

    session.addEventListener('end', () => this._handleEnd());

    session.requestAnimationFrame(this._frameLoop);
  }

  async end() {
    if (this.session) {
      await this.session.end();
    }
  }

  _handleEnd() {
    this.session = null;
    this.refSpace = null;
    this.gl = null;
    this.canvas = null;
    this.program = null;
    this.buffers = {};
    this.onEndCallbacks.forEach((cb) => {
      try {
        cb();
      } catch (e) {
        /* ignore */
      }
    });
  }

  _initGL() {
    const gl = this.gl;

    const vs = `
        attribute vec3 aPosition;
        attribute vec3 aColor;
        uniform mat4 uProjection;
        uniform mat4 uView;
        uniform mat4 uModel;
        uniform float uPointSize;
        varying vec3 vColor;
        void main() {
          vColor = aColor;
          vec4 mv = uView * uModel * vec4(aPosition, 1.0);
          gl_Position = uProjection * mv;
          // Scale point size by distance for a pseudo-perspective look.
          float dist = -mv.z;
          gl_PointSize = clamp(uPointSize / max(dist, 0.1), 2.0, 40.0);
        }
      `;

    const fs = `
        precision mediump float;
        varying vec3 vColor;
        void main() {
          vec2 c = gl_PointCoord - vec2(0.5);
          float d = dot(c, c);
          if (d > 0.25) discard;
          float a = smoothstep(0.25, 0.0, d);
          gl_FragColor = vec4(vColor, a);
        }
      `;

    const lineVs = `
        attribute vec3 aPosition;
        uniform mat4 uProjection;
        uniform mat4 uView;
        uniform mat4 uModel;
        void main() {
          gl_Position = uProjection * uView * uModel * vec4(aPosition, 1.0);
        }
      `;
    const lineFs = `
        precision mediump float;
        uniform vec4 uColor;
        void main() {
          gl_FragColor = uColor;
        }
      `;

    this.program = this._buildProgram(vs, fs);
    this.lineProgram = this._buildProgram(lineVs, lineFs);

    this.attribs = {
      position: gl.getAttribLocation(this.program, 'aPosition'),
      color: gl.getAttribLocation(this.program, 'aColor'),
    };
    this.uniforms = {
      projection: gl.getUniformLocation(this.program, 'uProjection'),
      view: gl.getUniformLocation(this.program, 'uView'),
      model: gl.getUniformLocation(this.program, 'uModel'),
      pointSize: gl.getUniformLocation(this.program, 'uPointSize'),
    };
    this.lineAttribs = {
      position: gl.getAttribLocation(this.lineProgram, 'aPosition'),
    };
    this.lineUniforms = {
      projection: gl.getUniformLocation(this.lineProgram, 'uProjection'),
      view: gl.getUniformLocation(this.lineProgram, 'uView'),
      model: gl.getUniformLocation(this.lineProgram, 'uModel'),
      color: gl.getUniformLocation(this.lineProgram, 'uColor'),
    };

    this.buffers.position = gl.createBuffer();
    this.buffers.color = gl.createBuffer();
    this.buffers.lines = gl.createBuffer();

    gl.enable(gl.DEPTH_TEST);
    gl.enable(gl.BLEND);
    gl.blendFunc(gl.SRC_ALPHA, gl.ONE_MINUS_SRC_ALPHA);
  }

  _buildProgram(vsSource, fsSource) {
    const gl = this.gl;
    const compile = (type, src) => {
      const sh = gl.createShader(type);
      gl.shaderSource(sh, src);
      gl.compileShader(sh);
      if (!gl.getShaderParameter(sh, gl.COMPILE_STATUS)) {
        throw new Error('Shader compile error: ' + gl.getShaderInfoLog(sh));
      }
      return sh;
    };
    const prog = gl.createProgram();
    gl.attachShader(prog, compile(gl.VERTEX_SHADER, vsSource));
    gl.attachShader(prog, compile(gl.FRAGMENT_SHADER, fsSource));
    gl.linkProgram(prog);
    if (!gl.getProgramParameter(prog, gl.LINK_STATUS)) {
      throw new Error('Program link error: ' + gl.getProgramInfoLog(prog));
    }
    return prog;
  }

  _updateGeometry() {
    const gl = this.gl;
    const st = this.getState();
    if (!st || !st.points) return 0;

    const arr = st.points.dataSync();
    const n = arr.length / 3;

    // Densities -> color (cyan to magenta), matching the 2D viz.
    const densities =
      st.densities && st.densities.length === n ? st.densities : new Float32Array(n).fill(1);
    let minD = Infinity;
    let maxD = -Infinity;
    for (let i = 0; i < n; i++) {
      if (densities[i] < minD) minD = densities[i];
      if (densities[i] > maxD) maxD = densities[i];
    }
    const range = maxD - minD + 1e-4;

    const colors = new Float32Array(n * 3);
    for (let i = 0; i < n; i++) {
      const t = (densities[i] - minD) / range;
      colors[i * 3] = t; // R
      colors[i * 3 + 1] = (210 - t * 210) / 255; // G
      colors[i * 3 + 2] = 1.0; // B
    }

    gl.bindBuffer(gl.ARRAY_BUFFER, this.buffers.position);
    gl.bufferData(gl.ARRAY_BUFFER, arr, gl.DYNAMIC_DRAW);
    gl.bindBuffer(gl.ARRAY_BUFFER, this.buffers.color);
    gl.bufferData(gl.ARRAY_BUFFER, colors, gl.DYNAMIC_DRAW);

    // Line segments for triangulation (if provided).
    this._lineCount = 0;
    if (st.triangles && st.triangles.length > 0) {
      const segs = [];
      for (const t of st.triangles) {
        const ai = t[0] * 3;
        const bi = t[1] * 3;
        const ci = t[2] * 3;
        segs.push(arr[ai], arr[ai + 1], arr[ai + 2], arr[bi], arr[bi + 1], arr[bi + 2]);
        segs.push(arr[bi], arr[bi + 1], arr[bi + 2], arr[ci], arr[ci + 1], arr[ci + 2]);
        segs.push(arr[ci], arr[ci + 1], arr[ci + 2], arr[ai], arr[ai + 1], arr[ai + 2]);
      }
      const lineArr = new Float32Array(segs);
      gl.bindBuffer(gl.ARRAY_BUFFER, this.buffers.lines);
      gl.bufferData(gl.ARRAY_BUFFER, lineArr, gl.DYNAMIC_DRAW);
      this._lineCount = lineArr.length / 3;
    }

    return n;
  }

  _modelMatrix() {
    // Place the unit-radius object ~1.4m in front, at eye height, scaled up.
    const scale = 0.6;
    const tx = 0;
    const ty = 1.4;
    const tz = -1.6;
    // Column-major 4x4.
    return new Float32Array([scale, 0, 0, 0, 0, scale, 0, 0, 0, 0, scale, 0, tx, ty, tz, 1]);
  }

  _frameLoop(time, frame) {
    const session = this.session;
    if (!session) return;
    session.requestAnimationFrame(this._frameLoop);

    const gl = this.gl;
    const pose = frame.getViewerPose(this.refSpace);
    if (!pose) return;

    const layer = session.renderState.baseLayer;
    gl.bindFramebuffer(gl.FRAMEBUFFER, layer.framebuffer);

    const isAR = this.mode === 'immersive-ar';
    if (isAR) {
      gl.clearColor(0, 0, 0, 0);
    } else {
      gl.clearColor(0.06, 0.07, 0.09, 1.0);
    }
    gl.clear(gl.COLOR_BUFFER_BIT | gl.DEPTH_BUFFER_BIT);

    const pointCount = this._updateGeometry();
    const model = this._modelMatrix();

    for (const view of pose.views) {
      const vp = layer.getViewport(view);
      gl.viewport(vp.x, vp.y, vp.width, vp.height);

      // Draw triangulation lines.
      if (this._lineCount > 0) {
        gl.useProgram(this.lineProgram);
        gl.uniformMatrix4fv(this.lineUniforms.projection, false, view.projectionMatrix);
        gl.uniformMatrix4fv(this.lineUniforms.view, false, view.transform.inverse.matrix);
        gl.uniformMatrix4fv(this.lineUniforms.model, false, model);
        gl.uniform4f(this.lineUniforms.color, 0.0, 0.82, 1.0, 0.25);
        gl.bindBuffer(gl.ARRAY_BUFFER, this.buffers.lines);
        gl.enableVertexAttribArray(this.lineAttribs.position);
        gl.vertexAttribPointer(this.lineAttribs.position, 3, gl.FLOAT, false, 0, 0);
        gl.drawArrays(gl.LINES, 0, this._lineCount);
      }

      // Draw points.
      gl.useProgram(this.program);
      gl.uniformMatrix4fv(this.uniforms.projection, false, view.projectionMatrix);
      gl.uniformMatrix4fv(this.uniforms.view, false, view.transform.inverse.matrix);
      gl.uniformMatrix4fv(this.uniforms.model, false, model);
      gl.uniform1f(this.uniforms.pointSize, 12.0);

      gl.bindBuffer(gl.ARRAY_BUFFER, this.buffers.position);
      gl.enableVertexAttribArray(this.attribs.position);
      gl.vertexAttribPointer(this.attribs.position, 3, gl.FLOAT, false, 0, 0);

      gl.bindBuffer(gl.ARRAY_BUFFER, this.buffers.color);
      gl.enableVertexAttribArray(this.attribs.color);
      gl.vertexAttribPointer(this.attribs.color, 3, gl.FLOAT, false, 0, 0);

      gl.drawArrays(gl.POINTS, 0, pointCount);
    }
  }
}
