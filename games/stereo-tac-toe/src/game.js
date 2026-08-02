// Pure tic-tac-toe state machine. Emits no DOM.

const WIN_LINES = [
  [0, 1, 2],
  [3, 4, 5],
  [6, 7, 8], // rows
  [0, 3, 6],
  [1, 4, 7],
  [2, 5, 8], // cols
  [0, 4, 8],
  [2, 4, 6], // diagonals
];

export class Game {
  constructor() {
    this.reset();
  }

  reset() {
    this.board = new Array(9).fill(null);
    this.turn = 'X';
    this.winner = null; // 'X' | 'O' | 'draw' | null
    this.winLine = null; // array of 3 indices, or null
    this.cursor = null; // hovered cell index, or null
  }

  move(cellIndex) {
    if (this.winner) return false;
    if (cellIndex == null || cellIndex < 0 || cellIndex > 8) return false;
    if (this.board[cellIndex] !== null) return false;

    this.board[cellIndex] = this.turn;
    this.checkWinner();
    if (!this.winner) {
      this.turn = this.turn === 'X' ? 'O' : 'X';
    }
    return true;
  }

  setCursor(cellIndex) {
    this.cursor = cellIndex;
  }

  checkWinner() {
    for (const [a, b, c] of WIN_LINES) {
      const v = this.board[a];
      if (v && v === this.board[b] && v === this.board[c]) {
        this.winner = v;
        this.winLine = [a, b, c];
        return;
      }
    }
    if (this.board.every((cell) => cell !== null)) {
      this.winner = 'draw';
      this.winLine = null;
    }
  }

  statusText() {
    if (this.winner === 'draw') return 'Draw';
    if (this.winner) return `${this.winner} wins`;
    return `${this.turn} to move`;
  }
}
