# System Architecture Overview

This document describes the high-level architecture of the Cognotik platform. 
The system is designed to be modular and extensible, allowing for various task types.

## Core Components

The platform consists of several key components:
1. **Task Orchestrator**: Manages the execution flow.
2. **Agent System**: Handles communication with LLMs.
3. **Web UI**: Provides a user interface for interaction.

## Data Flow

Data flows from the user through the UI to the orchestrator, which then delegates to specific agents.