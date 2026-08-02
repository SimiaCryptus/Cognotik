# Academic Citations for Non-QQN Optimizer Strategies

## L-BFGS (Limited-memory Broyden-Fletcher-Goldfarb-Shanno) Algorithm

### Foundational Papers

**Liu, D. C., & Nocedal, J. (1989).** _On the limited memory BFGS method for large scale optimization._ Mathematical Programming, 45(1-3), 503-528. https://doi.org/10.1007/BF01589116

This seminal paper introduces the L-BFGS algorithm, presenting the limited-memory quasi-Newton method for large-scale optimization and establishing global convergence on uniformly convex problems.

**Broyden, C. G. (1970).** _The convergence of a class of double-rank minimization algorithms 1. General considerations._ IMA Journal of Applied Mathematics, 6(1), 76-90. https://doi.org/10.1093/imamat/6.1.76

**Fletcher, R. (1970).** _A new approach to variable metric algorithms._ Computer Journal, 13(3), 317-322.

**Goldfarb, D. (1970).** _A family of variable-metric methods derived by variational means._ Mathematics of Computation, 24(109), 23-26.

**Shanno, D. F. (1970).** _Conditioning of quasi-Newton methods for function minimization._ Mathematics of Computation, 24(111), 647-656.

These four papers from 1970 independently developed the BFGS algorithm, forming the theoretical foundation for all subsequent BFGS and L-BFGS developments.

### Two-Loop Recursion and Core Algorithm

**Nocedal, J., & Wright, S. J. (2006).** _Numerical Optimization, Second Edition._ Springer Series in Operations Research and Financial Engineering. ISBN: 978-0387303031.

Chapter 7 provides detailed analysis of the two-loop recursion algorithm for L-BFGS, including parameter differences, gradient differences, and curvature information handling.

**Byrd, R. H., Nocedal, J., & Schnabel, R. B. (1994).** _Representations of quasi-Newton matrices and their use in limited memory methods._ Mathematical Programming, 63(1-3), 129-156.

### Powell's Damping and Numerical Safeguards

**Powell, M. J. D. (1978).** _Algorithms for nonlinear constraints that use Lagrange functions._ Mathematical Programming, 14(1), 224-248.

Introduces Powell's damping technique to handle negative curvature in BFGS updates.

**Al-Baali, M., Grandinetti, L., & Pisacane, O. (2014).** _Damped techniques for the limited memory BFGS method for large-scale optimization._ Journal of Optimization Theory and Applications, 161(2), 688-699. https://doi.org/10.1007/s10957-013-0448-8

### Gamma Scaling and Recovery Mechanisms

**Al-Baali, M. (1999).** _Improved Hessian approximations for the limited memory BFGS method._ Numerical Algorithms, 22, 99-112. https://doi.org/10.1023/A:1019142304382

**Byrd, R. H., Lu, P., Nocedal, J., & Zhu, C. (1995).** _A limited memory algorithm for bound constrained optimization._ SIAM Journal on Scientific Computing, 16(5), 1190-1208. https://doi.org/10.1137/0916069

Introduces L-BFGS-B with numerical safeguards and recovery mechanisms for bound constraints.

### Convergence Theory

**Nash, S. G., & Nocedal, J. (1991).** _A numerical study of the limited memory BFGS method and the truncated-Newton method for large scale optimization._ SIAM Journal on Optimization, 1(3), 358-372. https://doi.org/10.1137/0801023

**Li, D. H., & Fukushima, M. (2001).** _On the global convergence of the BFGS method for nonconvex unconstrained optimization problems._ SIAM Journal on Optimization, 11(4), 1054-1064. https://doi.org/10.1137/S1052623499354242

## Gradient Descent Variants and Momentum Methods

### Classical Foundations

**Cauchy, A. L. (1847).** _Méthode générale pour la résolution des systèmes d'équations simultanées._ Comptes Rendus de l'Académie des Sciences, 25, 536-538.

The original gradient descent paper, predating even the formal concept of gradients.

**Polyak, B. T. (1964).** _Some methods of speeding up the convergence of iteration methods._ USSR Computational Mathematics and Mathematical Physics, 4(5), 1-17.

Introduces the Heavy Ball method, the foundational momentum technique for accelerating gradient descent convergence.

### Nesterov Momentum Acceleration

**Nesterov, Y. (1983).** _A method of solving a convex programming problem with convergence rate O(1/k²)._ Soviet Mathematics Doklady, 27, 372-376.

Original Nesterov accelerated gradient method achieving optimal O(1/k²) convergence rate for smooth convex optimization.

**Beck, A., & Teboulle, M. (2009).** _A fast iterative shrinkage-thresholding algorithm for linear inverse problems._ SIAM Journal on Imaging Sciences, 2(1), 183-202.

Provides rigorous convergence proof for Nesterov's accelerated gradient descent method (FISTA).

**Sutskever, I., Martens, J., Dahl, G., & Hinton, G. (2013).** _On the importance of initialization and momentum in deep learning._ In Proceedings of the 30th International Conference on Machine Learning (ICML), 1139-1147.

Popularized Nesterov accelerated gradient (NAG) for deep learning applications.

### Gradient Clipping and Numerical Stability

**Pascanu, R., Mikolov, T., & Bengio, Y. (2013).** _On the difficulty of training recurrent neural networks._ In Proceedings of the 30th International Conference on Machine Learning (ICML), 1310-1318. arXiv:1211.5063

Introduces gradient norm clipping to address exploding gradients in RNNs with theoretical justification.

**Zhang, J., He, T., Sra, S., & Jadbabaie, A. (2020).** _Why gradient clipping accelerates training: A theoretical justification for adaptivity._ In Proceedings of the International Conference on Learning Representations (ICLR).

### Weight Decay and Regularization

**Krogh, A., & Hertz, J. A. (1992).** _A simple weight decay can improve generalization._ In Advances in Neural Information Processing Systems (NeurIPS), 4, 950-957.

First systematic study of weight decay for neural networks.

## Adam (Adaptive Moment Estimation) Optimizer

### Core Algorithm and Foundational Work

**Kingma, D. P., & Ba, J. (2015).** _Adam: A method for stochastic optimization._ In Proceedings of the 3rd International Conference on Learning Representations (ICLR). arXiv:1412.6980

The seminal Adam paper introducing adaptive moment estimation with first and second moment estimates and bias correction formulas.

**Duchi, J., Hazan, E., & Singer, Y. (2011).** _Adaptive subgradient methods for online learning and stochastic optimization._ Journal of Machine Learning Research, 12(61), 2121-2159.

Foundational AdaGrad paper establishing adaptive per-parameter learning rates.

### AMSGrad Variant and Theoretical Improvements

**Reddi, S. J., Kale, S., & Kumar, S. (2018).** _On the convergence of Adam and beyond._ In Proceedings of the International Conference on Learning Representations (ICLR). arXiv:1904.09237

Shows Adam convergence issues in convex settings and proposes AMSGrad with long-term memory of past gradients.

### AdamW and Weight Decay

**Loshchilov, I., & Hutter, F. (2019).** _Decoupled weight decay regularization._ In Proceedings of the International Conference on Learning Representations (ICLR). arXiv:1711.05101

Demonstrates that L2 regularization and weight decay are not equivalent for adaptive gradient algorithms, leading to AdamW.

### Learning Rate Schedules

**Loshchilov, I., & Hutter, F. (2017).** _SGDR: Stochastic gradient descent with warm restarts._ In Proceedings of the International Conference on Learning Representations (ICLR). arXiv:1608.03983

Introduces cosine annealing learning rate schedule with warm restarts, achieving state-of-the-art results.

**Smith, L. N. (2017).** _Cyclical learning rates for training neural networks._ In Proceedings of the IEEE Winter Conference on Applications of Computer Vision (WACV), 464-472.

Systematic study of cyclical learning rate policies showing benefits over fixed schedules.

## Comparative Studies and Strategic Optimizer Selection

### Comprehensive Surveys

**Bottou, L., Curtis, F. E., & Nocedal, J. (2018).** _Optimization methods for large-scale machine learning._ SIAM Review, 60(2), 223-311. https://doi.org/10.1137/16M1080173

Highly influential comprehensive analysis of optimization methods for large-scale ML with extensive coverage of comparative performance.

**Sun, S., Cao, Z., Zhu, H., & Zhao, J. (2019).** _A survey of optimization methods from a machine learning perspective._ arXiv:1906.06821

Systematic review of first-order, second-order, and derivative-free methods with detailed convergence rate comparisons.

**Ruder, S. (2016).** _An overview of gradient descent optimization algorithms._ arXiv:1609.04747

Widely-cited survey covering SGD variants, adaptive methods, and momentum-based approaches.

### Empirical Comparative Studies

**Choi, D., Shallue, C. J., Nado, Z., Lee, J., Maddison, C. J., & Dahl, G. E. (2020).** _On empirical comparisons of optimizers for deep learning._ arXiv:1910.05446

Critical analysis showing hyperparameter tuning protocol significantly affects optimizer rankings.

**Schmidt, R., Schneider, F., & Hennig, P. (2021).** _Descending through a crowded valley - Benchmarking deep learning optimizers._ In Proceedings of the International Conference on Machine Learning (ICML).

Comprehensive benchmark of 15 optimizers across 50,000+ runs showing task-dependent performance.

### Problem-Specific Analyses

**Jain, P. & Kar, P. (2017).** _Non-convex optimization for machine learning._ Foundations and Trends in Machine Learning, 10(3-4), 142-336. arXiv:1712.07897

Comprehensive analysis of when first-order methods succeed versus when second-order methods like L-BFGS are needed.

## Implementation Details and Performance Optimization

### Numerical Stability and Safeguards

**Chen, T., Xu, B., Zhang, C., & Guestrin, C. (2016).** _Training deep nets with sublinear memory cost._ In Proceedings of the International Conference on Machine Learning (ICML). arXiv:1604.06174

Memory-efficient optimization through gradient checkpointing.

**Ward, R., Wu, X., & Bottou, L. (2020).** _AdaGrad stepsizes: Sharp convergence over nonconvex landscapes._ Journal of Machine Learning Research, 21(74), 1-30.

Convergence analysis addressing numerical stability concerns in adaptive methods.

### Line Search and Step Size Methods

**Wolfe, P. (1969).** _Convergence conditions for ascent methods._ SIAM Review, 11(2), 226-235.

**Armijo, L. (1966).** _Minimization of functions having Lipschitz continuous first partial derivatives._ Pacific Journal of Mathematics, 16(1), 1-3.

Foundational work on line search methods essential for L-BFGS implementations.

### Distributed and Parallel Optimization

**Dean, J., Corrado, G. S., Monga, R., Chen, K., Devin, M., Le, Q. V., ... & Ng, A. Y. (2012).** _Large scale distributed deep networks._ In Advances in Neural Information Processing Systems (NIPS), 1223-1231.

Foundational work on distributed optimization for large-scale machine learning.

**Berahas, A. S., Nocedal, J., & Takáč, M. (2016).** _A multi-batch L-BFGS method for machine learning._ In Advances in Neural Information Processing Systems (NIPS).

Modern L-BFGS variants for machine learning applications.

## Recent Theoretical Developments

### Continuous-Time Analysis

**Su, W., Boyd, S., & Candès, E. J. (2016).** _A differential equation for modeling Nesterov's accelerated gradient method._ Journal of Machine Learning Research, 17(153), 1-43.

Connects Nesterov acceleration to continuous-time differential equations providing geometric insights.

### Stochastic Variants

**Mokhtari, A., & Ribeiro, A. (2015).** _Global convergence of online limited memory BFGS._ Journal of Machine Learning Research, 16, 3151-3181.

**Schraudolph, N. N., Yu, J., & Günter, S. (2007).** _A stochastic quasi-Newton method for online convex optimization._ In Proceedings of the International Conference on Artificial Intelligence and Statistics (AISTATS).

These citations represent the most authoritative sources from top-tier venues in optimization, machine learning, and numerical analysis, providing comprehensive coverage of the theoretical foundations, practical implementations, and comparative analyses needed for technical research on non-QQN optimizer strategies.
