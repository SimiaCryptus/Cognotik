# OneDNN MNIST Neural Network

This directory contains an alternate implementation of the MNIST neural network training problem that leverages Intel's OneDNN (Deep Neural Network Library) for optimized performance.

## Overview

The OneDNN implementation provides the same interface as the Candle-based implementation but uses Intel's OneDNN library for:

- Optimized matrix operations (GEMM)
- Efficient activation functions
- Memory layout optimization
- CPU-specific optimizations

## Key Features

### Performance Optimizations

- **Optimized GEMM operations**: OneDNN provides highly optimized general matrix multiplication routines
- **Efficient activation functions**: Hardware-optimized ReLU, Tanh, and Logistic implementations
- **Memory layout optimization**: OneDNN automatically chooses optimal memory formats
- **CPU architecture awareness**: Automatically detects and uses CPU features like AVX, AVX2, AVX-512

### Network Architectures Supported

- Fully connected (dense) layers
- Multiple activation functions: ReLU, Tanh, Logistic
- Configurable network depth and width
- Batch processing support

### Activation Functions

- **ReLU**: `f(x) = max(0, x)` - Uses OneDNN's optimized element-wise ReLU primitive
- **Tanh**: `f(x) = tanh(x)` - Uses OneDNN's optimized hyperbolic tangent
- **Logistic**: `f(x) = 1 / (1 + exp(-x))` - Sigmoid activation for output layers

## Installation

### Prerequisites

OneDNN must be installed on your system before building with the `onednn` feature.

#### Option 1: Using the installation script (Ubuntu/Debian)

```bash
python3 install_onednn.py
```

#### Option 2: Manual installation with Intel oneAPI

```bash
# Install Intel oneAPI
wget -O- https://apt.repos.intel.com/intel-gpg-keys/GPG-PUB-KEY-INTEL-SW-PRODUCTS.PUB | gpg --dearmor | sudo tee /usr/share/keyrings/oneapi-archive-keyring.gpg > /dev/null
echo "deb [signed-by=/usr/share/keyrings/oneapi-archive-keyring.gpg] https://apt.repos.intel.com/oneapi all main" | sudo tee /etc/apt/sources.list.d/oneAPI.list
sudo apt update
sudo apt install intel-oneapi-dnnl-devel

# Set environment variables
export DNNL_ROOT=/opt/intel/oneapi/dnnl/latest
export PKG_CONFIG_PATH=$DNNL_ROOT/lib/pkgconfig:$PKG_CONFIG_PATH
export LD_LIBRARY_PATH=$DNNL_ROOT/lib:$LD_LIBRARY_PATH
```

#### Option 3: From source

```bash
git clone https://github.com/oneapi-src/oneDNN.git
cd oneDNN
mkdir build && cd build
cmake .. -DCMAKE_INSTALL_PREFIX=/usr/local
make -j$(nproc)
sudo make install
```

### Building with OneDNN

```bash
# Build with OneDNN support
cargo build --features onednn

# Run tests with OneDNN
cargo test --features onednn

# Build with OneDNN and plotting features
cargo build --features "onednn,plotting"
```

## Usage

### Basic Usage

```rust
use qqn_optimizer::MnistOneDnnNeuralNetwork;
use qqn_optimizer::benchmarks::mnist_onednn::ActivationType;
use rand::{rngs::StdRng, SeedableRng};

// Create a neural network with OneDNN backend
let mut rng = StdRng::seed_from_u64(42);
let network = MnistOneDnnNeuralNetwork::create(
    Some(1000),                    // 1000 samples
    &[20, 20],                     // Two hidden layers with 20 neurons each
    Some(32),                      // Batch size of 32
    &mut rng,
    Some(ActivationType::ReLU),    // ReLU activation
)?;

// Use in optimization
let initial_params = network.initial_point();
let loss = network.evaluate_f64(&initial_params)?;
let gradient = network.gradient_f64(&initial_params)?;
```

### Integration with QQN Optimizer

```rust
use qqn_optimizer::{QQNOptimizer, MnistOneDnnNeuralNetwork};
use qqn_optimizer::line_search::strong_wolfe::StrongWolfeLineSearch;

// Create OneDNN-based problem
let mut rng = StdRng::seed_from_u64(42);
let problem = MnistOneDnnNeuralNetwork::create(
    Some(500),
    &[32],
    Some(64),
    &mut rng,
    Some(ActivationType::ReLU),
)?;

// Optimize with QQN
let line_search = StrongWolfeLineSearch::new();
let mut optimizer = QQNOptimizer::new(line_search);

let result = optimizer.optimize(
    &|x: &[f64]| problem.evaluate_f64(x).unwrap(),
    &|x: &[f64]| problem.gradient_f64(x).unwrap(),
    problem.initial_point(),
    1000,  // max function evaluations
    1e-6   // gradient tolerance
);
```

### Benchmarking OneDNN vs Candle

```rust
use qqn_optimizer::experiment_runner::problem_sets::{mnist_problems, mnist_onednn_problems};

// Create both problem sets for comparison
let candle_problems = mnist_problems(1000);
let onednn_problems = mnist_onednn_problems(1000);

// Run benchmarks on both implementations
// (This would be part of a larger benchmarking script)
```

## Architecture Comparison

### OneDNN vs Candle Implementation

| Aspect           | OneDNN Implementation   | Candle Implementation     |
| ---------------- | ----------------------- | ------------------------- |
| **Backend**      | Intel OneDNN primitives | Candle tensor operations  |
| **Optimization** | CPU-optimized BLAS      | General tensor operations |
| **Memory**       | OneDNN memory formats   | Standard tensor layouts   |
| **Activation**   | Hardware-optimized      | Software implementation   |
| **Parallelism**  | OneDNN threading        | Rayon parallel processing |
| **Platform**     | Intel CPU optimized     | Cross-platform            |

### Performance Characteristics

**OneDNN Advantages:**

- Significantly faster on Intel CPUs
- Better cache utilization
- Optimized for specific instruction sets (AVX, AVX2, AVX-512)
- Lower memory bandwidth usage
- Mature, production-tested optimizations

**Candle Advantages:**

- More portable across different hardware
- Easier to debug and profile
- More flexible for custom operations
- Better integration with Rust ecosystem
- Simpler dependency management

## Configuration Options

### Network Architecture

```rust
// Single hidden layer
let network = MnistOneDnnNeuralNetwork::create_single_hidden(
    Some(1000),    // samples
    64,            // hidden layer size
    Some(32),      // batch size
    &mut rng,
    Some(ActivationType::ReLU),
)?;

// Multiple hidden layers
let network = MnistOneDnnNeuralNetwork::create(
    Some(1000),
    &[128, 64, 32],  // Three hidden layers
    Some(64),
    &mut rng,
    Some(ActivationType::Tanh),
)?;
```

### Activation Functions

```rust
// ReLU activation (recommended for hidden layers)
ActivationType::ReLU     // f(x) = max(0, x)

// Tanh activation (good for symmetric data)
ActivationType::Tanh     // f(x) = tanh(x)

// Logistic activation (sigmoid, used for output)
ActivationType::Logistic // f(x) = 1 / (1 + exp(-x))
```

### Training Configuration

```rust
let network = MnistOneDnnNeuralNetwork::new(
    x_data,          // Training images
    y_data,          // Training labels
    &[64, 32],       // Hidden layer sizes
    Some(128),       // Batch size (larger for OneDNN efficiency)
    &mut rng,
    Some(ActivationType::ReLU),
)?;

// Configure regularization
network.l2_regularization = 1e-4;  // L2 regularization strength
```

## Performance Tips

### Optimal Configuration for OneDNN

1. **Batch Size**: Use larger batch sizes (64-256) to maximize OneDNN efficiency
2. **Layer Sizes**: Use multiples of vector sizes (8, 16, 32) for better vectorization
3. **Memory**: Ensure sufficient RAM for OneDNN's optimized memory layouts
4. **Threading**: Let OneDNN handle threading automatically

### Profiling and Debugging

```bash
# Enable OneDNN verbose output
export DNNL_VERBOSE=1

# Profile memory usage
export DNNL_VERBOSE=2

# Set number of threads explicitly
export OMP_NUM_THREADS=4
```

## Troubleshooting

### Common Issues

1. **OneDNN not found**

   ```
   Solution: Ensure PKG_CONFIG_PATH includes OneDNN pkgconfig directory
   export PKG_CONFIG_PATH=/opt/intel/oneapi/dnnl/latest/lib/pkgconfig:$PKG_CONFIG_PATH
   ```

2. **Runtime library errors**

   ```
   Solution: Add OneDNN lib to LD_LIBRARY_PATH
   export LD_LIBRARY_PATH=/opt/intel/oneapi/dnnl/latest/lib:$LD_LIBRARY_PATH
   ```

3. **Compilation errors**
   ```
   Solution: Install development headers
   sudo apt install intel-oneapi-dnnl-devel
   ```

### Performance Issues

1. **Slow execution**: Check that OneDNN is using optimized kernels

   ```bash
   DNNL_VERBOSE=1 ./your_program
   ```

2. **Memory usage**: OneDNN may use more memory for optimization
   ```rust
   // Use smaller batch sizes if memory constrained
   let batch_size = Some(32);  // Instead of 128
   ```

## Testing

```bash
# Run OneDNN-specific tests (requires OneDNN installation)
cargo test --features onednn test_onednn

# Run parameter validation tests
cargo test --features onednn test_parameter_validation

# Performance comparison tests
cargo test --features onednn --release performance_comparison
```

## Contributing

When contributing to the OneDNN implementation:

1. Ensure compatibility with the existing OptimizationProblem interface
2. Maintain feature parity with the Candle implementation
3. Add appropriate conditional compilation for the `onednn` feature
4. Include performance benchmarks for significant changes
5. Test on multiple Intel CPU architectures when possible

## References

- [Intel OneDNN Documentation](https://oneapi-src.github.io/oneDNN/)
- [OneDNN Performance Guide](https://oneapi-src.github.io/oneDNN/dev_guide_performance.html)
- [Intel oneAPI Toolkit](https://www.intel.com/content/www/us/en/developer/tools/oneapi/toolkit.html)
