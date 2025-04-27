// game.js

// Simple JavaScript Game: Collect the Squares
// Player controls a blue square with arrow keys to collect red squares for points.

const canvas = document.getElementById('game-canvas');
const ctx = canvas.getContext('2d');
const startBtn = document.getElementById('start-btn');
const scoreDisplay = document.getElementById('score');
const messageDisplay = document.getElementById('message');

const GAME_WIDTH = canvas.width;
const GAME_HEIGHT = canvas.height;
const PLAYER_SIZE = 30;
const ITEM_SIZE = 20;
const PLAYER_SPEED = 4;
const ITEM_COUNT = 5;

let player, items, score, gameActive, animationId;

// Initialize game state
function initGame() {
  player = {
    x: GAME_WIDTH / 2 - PLAYER_SIZE / 2,
    y: GAME_HEIGHT / 2 - PLAYER_SIZE / 2,
    width: PLAYER_SIZE,
    height: PLAYER_SIZE,
    dx: 0,
    dy: 0,
  };
  items = [];
  for (let i = 0; i < ITEM_COUNT; i++) {
    items.push(spawnItem());
  }
  score = 0;
  updateScore();
  messageDisplay.textContent = '';
  gameActive = true;
}

// Spawn a collectible item at a random position
function spawnItem() {
  let x, y;
  let safe = false;
  while (!safe) {
    x = Math.floor(Math.random() * (GAME_WIDTH - ITEM_SIZE));
    y = Math.floor(Math.random() * (GAME_HEIGHT - ITEM_SIZE));
    // Ensure item does not spawn on player
    if (
      x + ITEM_SIZE < player?.x ||
      x > player?.x + PLAYER_SIZE ||
      y + ITEM_SIZE < player?.y ||
      y > player?.y + PLAYER_SIZE
    ) {
      safe = true;
    }
  }
  return { x, y, width: ITEM_SIZE, height: ITEM_SIZE };
}

// Draw player and items
function draw() {
  ctx.clearRect(0, 0, GAME_WIDTH, GAME_HEIGHT);

  // Draw player
  ctx.fillStyle = '#3498db';
  ctx.fillRect(player.x, player.y, player.width, player.height);

  // Draw items
  ctx.fillStyle = '#e74c3c';
  items.forEach(item => {
    ctx.fillRect(item.x, item.y, item.width, item.height);
  });
}

// Update game state
function update() {
  // Move player
  player.x += player.dx;
  player.y += player.dy;

  // Keep player in bounds
  player.x = Math.max(0, Math.min(GAME_WIDTH - PLAYER_SIZE, player.x));
  player.y = Math.max(0, Math.min(GAME_HEIGHT - PLAYER_SIZE, player.y));

  // Check for collisions with items
  for (let i = items.length - 1; i >= 0; i--) {
    if (isColliding(player, items[i])) {
      items.splice(i, 1);
      score++;
      updateScore();
      // Spawn a new item
      items.push(spawnItem());
    }
  }

  draw();

  // Win condition
  if (score >= 10) {
    endGame('You win! 🎉');
  } else {
    animationId = requestAnimationFrame(update);
  }
}

// Collision detection
function isColliding(a, b) {
  return (
    a.x < b.x + b.width &&
    a.x + a.width > b.x &&
    a.y < b.y + b.height &&
    a.y + a.height > b.y
  );
}

// Handle keyboard input
function handleKeyDown(e) {
  if (!gameActive) return;
  switch (e.key) {
    case 'ArrowUp':
    case 'w':
      player.dy = -PLAYER_SPEED;
      break;
    case 'ArrowDown':
    case 's':
      player.dy = PLAYER_SPEED;
      break;
    case 'ArrowLeft':
    case 'a':
      player.dx = -PLAYER_SPEED;
      break;
    case 'ArrowRight':
    case 'd':
      player.dx = PLAYER_SPEED;
      break;
  }
}

function handleKeyUp(e) {
  if (!gameActive) return;
  switch (e.key) {
    case 'ArrowUp':
    case 'w':
    case 'ArrowDown':
    case 's':
      player.dy = 0;
      break;
    case 'ArrowLeft':
    case 'a':
    case 'ArrowRight':
    case 'd':
      player.dx = 0;
      break;
  }
}

// Update score display
function updateScore() {
  scoreDisplay.textContent = `Score: ${score}`;
}

// End game
function endGame(msg) {
  gameActive = false;
  messageDisplay.textContent = msg;
  cancelAnimationFrame(animationId);
}

// Start game handler
function startGame() {
  initGame();
  draw();
  animationId = requestAnimationFrame(update);
}

// Event listeners
startBtn.addEventListener('click', startGame);
window.addEventListener('keydown', handleKeyDown);
window.addEventListener('keyup', handleKeyUp);

// Initial state
draw();
messageDisplay.textContent = 'Press "Start Game" to play!';