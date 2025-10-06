// Game constants
const GRID_SIZE = 20;
const CELL_SIZE = 20;
const INITIAL_SPEED = 150;
const SPEED_INCREMENT = 5;

// Game state
let canvas, ctx;
let snake = [];
let food = {};
let direction = 'right';
let nextDirection = 'right';
let score = 0;
let highScore = 0;
let gameLoop = null;
let isPaused = false;
let gameSpeed = INITIAL_SPEED;

// DOM elements
const scoreElement = document.getElementById('score');
const highScoreElement = document.getElementById('highScore');
const startBtn = document.getElementById('startBtn');
const pauseBtn = document.getElementById('pauseBtn');
const restartBtn = document.getElementById('restartBtn');
const gameOverElement = document.getElementById('gameOver');
const finalScoreElement = document.getElementById('finalScore');

// Initialize game
function init() {
    canvas = document.getElementById('gameCanvas');
    ctx = canvas.getContext('2d');
    
    // Load high score from localStorage
    highScore = localStorage.getItem('snakeHighScore') || 0;
    highScoreElement.textContent = highScore;
    
    // Event listeners
    startBtn.addEventListener('click', startGame);
    pauseBtn.addEventListener('click', togglePause);
    restartBtn.addEventListener('click', restartGame);
    document.addEventListener('keydown', handleKeyPress);
}

// Start new game
function startGame() {
    // Reset game state
    snake = [
        { x: 10, y: 10 },
        { x: 9, y: 10 },
        { x: 8, y: 10 }
    ];
    direction = 'right';
    nextDirection = 'right';
    score = 0;
    gameSpeed = INITIAL_SPEED;
    isPaused = false;
    
    updateScore();
    spawnFood();
    
    // Update UI
    startBtn.disabled = true;
    pauseBtn.disabled = false;
    gameOverElement.classList.add('hidden');
    
    // Start game loop
    if (gameLoop) clearInterval(gameLoop);
    gameLoop = setInterval(update, gameSpeed);
}

// Game update loop
function update() {
    if (isPaused) return;
    
    // Update direction
    direction = nextDirection;
    
    // Calculate new head position
    const head = { ...snake[0] };
    
    switch (direction) {
        case 'up':
            head.y--;
            break;
        case 'down':
            head.y++;
            break;
        case 'left':
            head.x--;
            break;
        case 'right':
            head.x++;
            break;
    }
    
    // Check collisions
    if (checkCollision(head)) {
        gameOver();
        return;
    }
    
    // Add new head
    snake.unshift(head);
    
    // Check if food eaten
    if (head.x === food.x && head.y === food.y) {
        score += 10;
        updateScore();
        spawnFood();
        
        // Increase speed slightly
        if (score % 50 === 0 && gameSpeed > 50) {
            gameSpeed -= SPEED_INCREMENT;
            clearInterval(gameLoop);
            gameLoop = setInterval(update, gameSpeed);
        }
    } else {
        // Remove tail if no food eaten
        snake.pop();
    }
    
    draw();
}

// Draw game
function draw() {
    // Clear canvas
    ctx.fillStyle = '#000';
    ctx.fillRect(0, 0, canvas.width, canvas.height);
    
    // Draw grid (optional)
    ctx.strokeStyle = '#111';
    for (let i = 0; i <= GRID_SIZE; i++) {
        ctx.beginPath();
        ctx.moveTo(i * CELL_SIZE, 0);
        ctx.lineTo(i * CELL_SIZE, canvas.height);
        ctx.stroke();
        
        ctx.beginPath();
        ctx.moveTo(0, i * CELL_SIZE);
        ctx.lineTo(canvas.width, i * CELL_SIZE);
        ctx.stroke();
    }
    
    // Draw snake
    snake.forEach((segment, index) => {
        ctx.fillStyle = index === 0 ? '#4ade80' : '#22c55e';
        ctx.fillRect(
            segment.x * CELL_SIZE,
            segment.y * CELL_SIZE,
            CELL_SIZE - 2,
            CELL_SIZE - 2
        );
        
        // Add eye to head
        if (index === 0) {
            ctx.fillStyle = '#000';
            const eyeSize = 3;
            if (direction === 'right') {
                ctx.fillRect(segment.x * CELL_SIZE + 12, segment.y * CELL_SIZE + 5, eyeSize, eyeSize);
                ctx.fillRect(segment.x * CELL_SIZE + 12, segment.y * CELL_SIZE + 12, eyeSize, eyeSize);
            } else if (direction === 'left') {
                ctx.fillRect(segment.x * CELL_SIZE + 5, segment.y * CELL_SIZE + 5, eyeSize, eyeSize);
                ctx.fillRect(segment.x * CELL_SIZE + 5, segment.y * CELL_SIZE + 12, eyeSize, eyeSize);
            } else if (direction === 'up') {
                ctx.fillRect(segment.x * CELL_SIZE + 5, segment.y * CELL_SIZE + 5, eyeSize, eyeSize);
                ctx.fillRect(segment.x * CELL_SIZE + 12, segment.y * CELL_SIZE + 5, eyeSize, eyeSize);
            } else {
                ctx.fillRect(segment.x * CELL_SIZE + 5, segment.y * CELL_SIZE + 12, eyeSize, eyeSize);
                ctx.fillRect(segment.x * CELL_SIZE + 12, segment.y * CELL_SIZE + 12, eyeSize, eyeSize);
            }
        }
    });
    
    // Draw food
    ctx.fillStyle = '#ef4444';
    ctx.beginPath();
    ctx.arc(
        food.x * CELL_SIZE + CELL_SIZE / 2,
        food.y * CELL_SIZE + CELL_SIZE / 2,
        CELL_SIZE / 2 - 2,
        0,
        Math.PI * 2
    );
    ctx.fill();
}

// Spawn food at random position
function spawnFood() {
    do {
        food = {
            x: Math.floor(Math.random() * GRID_SIZE),
            y: Math.floor(Math.random() * GRID_SIZE)
        };
    } while (snake.some(segment => segment.x === food.x && segment.y === food.y));
}

// Check collision with walls or self
function checkCollision(head) {
    // Wall collision
    if (head.x < 0 || head.x >= GRID_SIZE || head.y < 0 || head.y >= GRID_SIZE) {
        return true;
    }
    
    // Self collision
    return snake.some(segment => segment.x === head.x && segment.y === head.y);
}

// Handle keyboard input
function handleKeyPress(e) {
    const key = e.key.toLowerCase();
    
    // Prevent default arrow key behavior
    if (['arrowup', 'arrowdown', 'arrowleft', 'arrowright'].includes(key)) {
        e.preventDefault();
    }
    
    // Change direction (prevent 180-degree turns)
    if ((key === 'arrowup' || key === 'w') && direction !== 'down') {
        nextDirection = 'up';
    } else if ((key === 'arrowdown' || key === 's') && direction !== 'up') {
        nextDirection = 'down';
    } else if ((key === 'arrowleft' || key === 'a') && direction !== 'right') {
        nextDirection = 'left';
    } else if ((key === 'arrowright' || key === 'd') && direction !== 'left') {
        nextDirection = 'right';
    } else if (key === ' ' || key === 'p') {
        togglePause();
    }
}

// Toggle pause
function togglePause() {
    if (!gameLoop) return;
    
    isPaused = !isPaused;
    pauseBtn.textContent = isPaused ? 'Resume' : 'Pause';
}

// Update score display
function updateScore() {
    scoreElement.textContent = score;
    
    if (score > highScore) {
        highScore = score;
        highScoreElement.textContent = highScore;
        localStorage.setItem('snakeHighScore', highScore);
    }
}

// Game over
function gameOver() {
    clearInterval(gameLoop);
    gameLoop = null;
    
    finalScoreElement.textContent = score;
    gameOverElement.classList.remove('hidden');
    
    startBtn.disabled = false;
    pauseBtn.disabled = true;
}

// Restart game
function restartGame() {
    startGame();
}

// Initialize when page loads
window.addEventListener('load', init);