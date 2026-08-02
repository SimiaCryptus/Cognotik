/**
 * WebXR (VR) support for the Knot Topology Lab.
 *
 * Renders the current knot (points + edges, or a tube mesh) in immersive VR
 * using a minimal WebGL renderer. Reads point data from a callback so it
 * always reflects the live optimization state.
 *
 * Usage:
 *   const xr = new KnotXR({
 *     getPoints: () => [[x,y,z],...],   // current knot points
 *     getShowEdges: () => boolean,
 *     getSolidView: () => boolean,
 *   });
 *   await xr.checkSupport();
 *   xr.enter();  // request immersive session
 */
export class KnotXR {
  constructor(opts = {}) {
    this.getPoints = opts.getPoints || (() => []);
    this.getShowEdges = opts.getShowEdges || (() => true);
    this.getSolidView = opts.getSolidView || (() => false);
    this.onSessionStart = opts.onSessionStart || (() => {});
    this.onSessionEnd = opts.onSessionEnd || (() => {});

    this.session = null;
    this.gl = null;
    this.canvas = null;
    this.refSpace = null;
    this.program = null;
    this.lineProgram = null;
    this.supported = false;

    // Scale + position of the knot in the VR world (meters)
    this.worldScale = 1.5;
    this.worldOffset = [0, 1.4, -2.0]; // place in front of viewer at eye height

    // Buffers
    this.pointBuffer = null;
    this.lineBuffer = null;

    // Animation handle
    this._onXRFrame = this._onXRFrame.bind(this);
  }

  /**
   * Check whether immersive-vr is available.
   */
  async checkSupport() {
    if (!('xr' in navigator)) {
      this.supported = false;
      return false;
    }
    try {
      this.supported = await navigator.xr.isSessionSupported('immersive-vr');
    } catch (e) {
      this.supported = false;
    }
    return this.supported;
  }

  /**
   * Compile a shader program.
   */
  _compile(gl, vsSrc, fsSrc) {
    const compile = (type, src) => {
      const s = gl.createShader(type);
      gl.shaderSource(s, src);
      gl.compileShader(s);
      if (!gl.getShaderParameter(s, gl.COMPILE_STATUS)) {
        throw new Error('Shader compile error: ' + gl.getShaderInfoLog(s));
      }
      return s;
    };
    const prog = gl.createProgram();
    gl.attachShader(prog, compile(gl.VERTEX_SHADER, vsSrc));
    gl.attachShader(prog, compile(gl.FRAGMENT_SHADER, fsSrc));
    gl.linkProgram(prog);
    if (!gl.getProgramParameter(prog, gl.LINK_STATUS)) {
      throw new Error('Program link error: ' + gl.getProgramInfoLog(prog));
    }
    return prog;
  }

  _initGL() {
    this.canvas = document.createElement('canvas');
    const gl =
      this.canvas.getContext('webgl2', { xrCompatible: true }) ||
      this.canvas.getContext('webgl', { xrCompatible: true });
    if (!gl) throw new Error('WebGL not available for XR');
    this.gl = gl;

    const vs = `
        attribute vec3 aPos;
        attribute vec3 aColor;
        uniform mat4 uProjection;
        uniform mat4 uView;
        uniform mat4 uModel;
        uniform float uPointSize;
        varying vec3 vColor;
        void main() {
          vColor = aColor;
          gl_Position = uProjection * uView * uModel * vec4(aPos, 1.0);
          gl_PointSize = uPointSize;
        }
      `;
    const fsPoints = `
        precision mediump float;
        varying vec3 vColor;
        void main() {
          vec2 c = gl_PointCoord - vec2(0.5);
          if (dot(c, c) > 0.25) discard;
          gl_FragColor = vec4(vColor, 1.0);
        }
      `;
    const fsLines = `
        precision mediump float;
        varying vec3 vColor;
        void main() {
          gl_FragColor = vec4(vColor, 1.0);
        }
      `;

    this.program = this._compile(gl, vs, fsPoints);
    this.lineProgram = this._compile(gl, vs, fsLines);

    this.pointBuffer = gl.createBuffer();
    this.lineBuffer = gl.createBuffer();

    gl.enable(gl.DEPTH_TEST);
  }

  /**
   * Build interleaved [x,y,z, r,g,b] arrays for points and line segments.
   */
  _buildGeometry() {
    const pts = this.getPoints();
    const n = pts.length;
    if (n === 0)
      return { points: new Float32Array(0), lines: new Float32Array(0), count: 0, lineCount: 0 };

    const colorFor = (i) => {
      const t = i / n;
      const r = 0.5 + 0.5 * Math.sin(t * Math.PI * 2);
      const g = 0.5 + 0.5 * Math.sin(t * Math.PI * 2 + (Math.PI * 2) / 3);
      const b = 0.5 + 0.5 * Math.sin(t * Math.PI * 2 + (Math.PI * 4) / 3);
      return [r, g, b];
    };

    const pointData = new Float32Array(n * 6);
    for (let i = 0; i < n; i++) {
      const p = pts[i];
      const c = colorFor(i);
      pointData[i * 6 + 0] = p[0];
      pointData[i * 6 + 1] = p[1];
      pointData[i * 6 + 2] = p[2];
      pointData[i * 6 + 3] = c[0];
      pointData[i * 6 + 4] = c[1];
      pointData[i * 6 + 5] = c[2];
    }

    // Closed loop line segments
    const lineData = new Float32Array(n * 2 * 6);
    for (let i = 0; i < n; i++) {
      const a = pts[i];
      const b = pts[(i + 1) % n];
      const base = i * 12;
      // edge color: cyan-ish
      lineData[base + 0] = a[0];
      lineData[base + 1] = a[1];
      lineData[base + 2] = a[2];
      lineData[base + 3] = 0.0;
      lineData[base + 4] = 0.82;
      lineData[base + 5] = 1.0;
      lineData[base + 6] = b[0];
      lineData[base + 7] = b[1];
      lineData[base + 8] = b[2];
      lineData[base + 9] = 0.0;
      lineData[base + 10] = 0.82;
      lineData[base + 11] = 1.0;
    }

    return { points: pointData, lines: lineData, count: n, lineCount: n * 2 };
  }

  _modelMatrix() {
    // Scale + translate (column-major)
    const s = this.worldScale;
    const [tx, ty, tz] = this.worldOffset;
    return new Float32Array([s, 0, 0, 0, 0, s, 0, 0, 0, 0, s, 0, tx, ty, tz, 1]);
  }

  /**
   * Enter immersive VR.
   */
  async enter() {
    if (this.session) return;
    if (!navigator.xr) throw new Error('WebXR not available');

    this._initGL();

    const session = await navigator.xr.requestSession('immersive-vr', {
      optionalFeatures: ['local-floor', 'bounded-floor'],
    });
    this.session = session;

    const gl = this.gl;
    const xrLayer = new XRWebGLLayer(session, gl);
    session.updateRenderState({ baseLayer: xrLayer });

    try {
      this.refSpace = await session.requestReferenceSpace('local-floor');
    } catch (e) {
      this.refSpace = await session.requestReferenceSpace('local');
    }

    session.addEventListener('end', () => {
      this.session = null;
      this.refSpace = null;
      this.onSessionEnd();
    });

    this.onSessionStart();
    session.requestAnimationFrame(this._onXRFrame);
  }

  /**
   * Exit VR.
   */
  async exit() {
    if (this.session) {
      await this.session.end();
    }
  }

  _onXRFrame(time, frame) {
    const session = this.session;
    if (!session) return;
    session.requestAnimationFrame(this._onXRFrame);

    const gl = this.gl;
    const pose = frame.getViewerPose(this.refSpace);
    if (!pose) return;

    const layer = session.renderState.baseLayer;
    gl.bindFramebuffer(gl.FRAMEBUFFER, layer.framebuffer);
    gl.clearColor(0.06, 0.07, 0.09, 1.0);
    gl.clear(gl.COLOR_BUFFER_BIT | gl.DEPTH_BUFFER_BIT);

    // Upload latest geometry once per frame
    const geo = this._buildGeometry();
    if (geo.count === 0) return;

    gl.bindBuffer(gl.ARRAY_BUFFER, this.pointBuffer);
    gl.bufferData(gl.ARRAY_BUFFER, geo.points, gl.DYNAMIC_DRAW);
    gl.bindBuffer(gl.ARRAY_BUFFER, this.lineBuffer);
    gl.bufferData(gl.ARRAY_BUFFER, geo.lines, gl.DYNAMIC_DRAW);

    const model = this._modelMatrix();

    for (const view of pose.views) {
      const vp = layer.getViewport(view);
      gl.viewport(vp.x, vp.y, vp.width, vp.height);

      const proj = view.projectionMatrix;
      const viewMat = view.transform.inverse.matrix;

      // Draw edges
      if (this.getShowEdges()) {
        gl.useProgram(this.lineProgram);
        this._setMatrices(this.lineProgram, proj, viewMat, model, 1.0);
        this._bindAttribs(this.lineProgram, this.lineBuffer);
        gl.drawArrays(gl.LINES, 0, geo.lineCount);
      }

      // Draw points
      gl.useProgram(this.program);
      this._setMatrices(this.program, proj, viewMat, model, 12.0);
      this._bindAttribs(this.program, this.pointBuffer);
      gl.drawArrays(gl.POINTS, 0, geo.count);
    }
  }

  _setMatrices(prog, proj, view, model, pointSize) {
    const gl = this.gl;
    gl.uniformMatrix4fv(gl.getUniformLocation(prog, 'uProjection'), false, proj);
    gl.uniformMatrix4fv(gl.getUniformLocation(prog, 'uView'), false, view);
    gl.uniformMatrix4fv(gl.getUniformLocation(prog, 'uModel'), false, model);
    const psLoc = gl.getUniformLocation(prog, 'uPointSize');
    if (psLoc) gl.uniform1f(psLoc, pointSize);
  }

  _bindAttribs(prog, buffer) {
    const gl = this.gl;
    gl.bindBuffer(gl.ARRAY_BUFFER, buffer);
    const stride = 6 * 4;
    const posLoc = gl.getAttribLocation(prog, 'aPos');
    gl.enableVertexAttribArray(posLoc);
    gl.vertexAttribPointer(posLoc, 3, gl.FLOAT, false, stride, 0);
    const colLoc = gl.getAttribLocation(prog, 'aColor');
    gl.enableVertexAttribArray(colLoc);
    gl.vertexAttribPointer(colLoc, 3, gl.FLOAT, false, stride, 3 * 4);
  }
}
