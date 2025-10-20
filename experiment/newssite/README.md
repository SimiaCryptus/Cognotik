# AI-Powered News API: A Full-Stack AI-Generated Application

[![Kotlin](https://img.shields.io/badge/Kotlin-1.9+-7F52FF.svg?logo=kotlin)](https://kotlinlang.org)
[![React](https://img.shields.io/badge/React-18.3+-61DAFB.svg?logo=react)](https://reactjs.org)
[![TypeScript](https://img.shields.io/badge/TypeScript-5.4+-3178C6.svg?logo=typescript)](https://www.typescriptlang.org)
[![Anthropic Claude](https://img.shields.io/badge/Powered%20by-Claude%204.5-191919.svg)](https://www.anthropic.com)

> **A demonstration of AI-driven software development**: From schema design to full-stack implementation, this project showcases how AI can collaboratively design, generate, and power an entire application.

## 🎯 Project Overview

This project demonstrates a revolutionary approach to software development where AI is involved at every stage:

1. **🤝 Collaborative Schema Design** - Work with AI to define the API interface
2. **⚡ AI-Powered Backend** - Use LLM proxies to implement business logic
3. **🤖 AI-Generated Code** - Automatically generate server and client implementations
4. **🚀 Full-Stack Application** - Deploy a complete, working application

The result is a **Hacker News-style API** that is entirely AI-generated and AI-powered, demonstrating the future of software development.

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Schema Definition                         │
│              (NewsApiService Interface)                      │
└─────────────────┬───────────────────────────────────────────┘
                  │
        ┌─────────┴─────────┐
        │                   │
        ▼                   ▼
┌───────────────┐   ┌──────────────────┐
│  AI Proxy     │   │  Code Generation │
│  (Runtime)    │   │  (Build Time)    │
└───────┬───────┘   └────────┬─────────┘
        │                    │
        ▼                    ▼
┌───────────────┐   ┌──────────────────┐
│ Spring Boot   │   │  React Frontend  │
│ REST Server   │◄──┤  TypeScript UI   │
└───────────────┘   └──────────────────┘
        │
        ▼
┌───────────────┐
│ Claude 4.5    │
│ Haiku (LLM)   │
└───────────────┘
```

## ✨ Key Features

### 🎨 Schema-First Design
- Define your API as a Kotlin interface
- Use annotations to specify behavior
- Let AI understand and implement the contract

### 🧠 AI-Powered Backend
- **No traditional database required**
- LLM generates realistic, contextual data on-the-fly
- Intelligent search and filtering capabilities
- Consistent, believable responses

### 🔄 Automatic Code Generation
- Server endpoints generated from schema
- TypeScript client SDK auto-generated
- React UI components scaffolded
- Full type safety across the stack

### 📊 Rich Data Model
- **Stories**: News articles with metadata, tags, and topics
- **Comments**: Threaded discussions with nested replies
- **Users**: Profiles with karma, submissions, and activity
- **Search**: Advanced filtering by tags, topics, location, and more

## 🚀 Getting Started

### Prerequisites

- **Java 17+** (for Kotlin/Spring Boot)
- **Node.js 18+** (for React frontend)
- **Anthropic API Key** (for Claude 4.5 Haiku)

### 1️⃣ Setup API Key

Create a file at `src/main/resources/anthropic.key` with your Anthropic API key:

```bash
echo "your-api-key-here" > src/main/resources/anthropic.key
```

> 💡 Get your API key from [Anthropic Console](https://console.anthropic.com/)

### 2️⃣ Build and Run Backend

```bash
# Build the project
./gradlew build

# Run the Spring Boot server
./gradlew bootRun
```

The API will be available at `http://localhost:8080`

### 3️⃣ Run Frontend

```bash
# Navigate to UI directory
cd ui

# Install dependencies
npm install

# Start development server
npm run dev
```

The UI will be available at `http://localhost:5173`

## 📖 API Documentation

Comprehensive API documentation is available in [`api.md`](api.md), including:

- **Stories API**: Top stories, new stories, search, and details
- **Comments API**: Threaded discussions and replies
- **Users API**: Profiles, submissions, and activity
- **Data Models**: Complete schema definitions
- **Examples**: Real-world usage patterns

### Quick Example

```bash
# Get top stories
curl http://localhost:8080/api/stories/top?page=1&pageSize=10

# Search stories
curl -X POST http://localhost:8080/api/stories/search \
  -H "Content-Type: application/json" \
  -d '{"query": "AI", "minScore": 100, "sortOrder": "SCORE"}'

# Get user profile
curl http://localhost:8080/api/users/techuser
```

## 🔍 How It Works

### 1. Schema Definition (`NewsApiService.kt`)

Define your API as a Kotlin interface with annotations:

```kotlin
interface NewsApiService {
    @Description("Get the current top stories")
    fun getTopStoryIds(
        @Description("Pagination parameters")
        pagination: PaginationParams = PaginationParams()
    ): PaginatedStoryIds

    @Description("Get detailed information about a specific story")
    fun getStoryDetails(
        @Description("The unique identifier of the story")
        id: Long
    ): Story
}
```

### 2. AI Proxy Implementation (`NewsServiceLoader.kt`)

The `ProxyAgent` creates a runtime implementation powered by Claude:

```kotlin
val newsApi = ProxyAgent(
    clazz = NewsApiService::class.java,
    model = AnthropicModels.Claude45Haiku.instance(key = apiKey),
    temperature = 0.8,
    validation = true
).create()
```

**What happens:**
- Method calls are intercepted at runtime
- Request is converted to natural language prompt
- Claude generates appropriate response
- Response is validated and returned as typed object

### 3. Code Generation

The build process automatically generates:

- **REST Controllers**: Spring Boot endpoints matching the schema
- **TypeScript Client**: Type-safe API client for frontend
- **React Components**: UI scaffolding for data display

### 4. Full-Stack Integration

```typescript
// Frontend TypeScript (auto-generated)
const stories = await newsApi.getTopStoryIds({ page: 1, pageSize: 30 });
const story = await newsApi.getStoryDetails(stories.ids[0]);
```

## 🎓 Learning Outcomes

This project demonstrates:

### For Developers
- **Schema-driven development** with AI assistance
- **LLM-powered backends** without traditional databases
- **Automatic code generation** from interface definitions
- **Type-safe full-stack** development

### For Architects
- **AI as infrastructure** - treating LLMs as compute resources
- **Declarative APIs** - define what, not how
- **Rapid prototyping** - from idea to working app in minutes
- **Cost-effective MVPs** - no database setup or data seeding

### For Product Teams
- **Instant realistic data** - no need for fixtures or mocks
- **Flexible schemas** - easy to iterate and refine
- **Natural language queries** - powerful search without complex indexing
- **Contextual responses** - data that makes sense together

## 🛠️ Technology Stack

### Backend
- **Kotlin** - Modern JVM language
- **Spring Boot** - Web framework
- **Cognotik** - AI proxy framework
- **Anthropic Claude 4.5 Haiku** - LLM provider

### Frontend
- **React 18** - UI framework
- **TypeScript** - Type safety
- **Vite** - Build tool
- **React Router** - Navigation

### AI/ML
- **Claude 4.5 Haiku** - Fast, cost-effective LLM
- **Structured outputs** - Type-safe AI responses
- **Validation** - Automatic response verification

## 📊 Project Structure

```
newssite/
├── src/main/kotlin/com/example/news/
│   ├── api/
│   │   ├── NewsApiService.kt          # 📋 Core schema definition
│   │   ├── NewsServiceLoader.kt       # 🤖 AI proxy setup
│   │   └── models/                    # 📦 Data models
│   └── server/
│       └── NewsApiApplication.kt      # 🚀 Spring Boot app
├── ui/
│   ├── src/
│   │   ├── api/                       # 🔌 Generated client
│   │   ├── components/                # 🎨 React components
│   │   └── App.tsx                    # 📱 Main application
│   └── package.json
├── api.md                             # 📖 API documentation
└── build.gradle.kts                   # 🔧 Build configuration
```

## 🎯 Use Cases

### Rapid Prototyping
- Build MVPs without database setup
- Iterate on API design quickly
- Get realistic data immediately

### API Design Workshops
- Collaborate with AI on schema design
- Test different API patterns
- Generate documentation automatically

### Educational Projects
- Learn full-stack development
- Understand AI integration
- Practice API design

### Proof of Concepts
- Demonstrate ideas quickly
- Test market fit
- Validate technical approaches

## 🔮 Future Enhancements

- [ ] **Persistent Storage**: Add optional database backing
- [ ] **Authentication**: User login and authorization
- [ ] **Real-time Updates**: WebSocket support for live data
- [ ] **Advanced Search**: Vector embeddings for semantic search
- [ ] **Multi-model Support**: Switch between different LLMs
- [ ] **Caching Layer**: Redis integration for performance
- [ ] **GraphQL API**: Alternative query interface
- [ ] **Mobile Apps**: React Native clients

## 🤝 Contributing

This is a demonstration project, but contributions are welcome!

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## 📝 License

This project is provided as-is for educational and demonstration purposes.

## 🙏 Acknowledgments

- **Anthropic** - For Claude 4.5 Haiku API
- **Cognotik** - AI proxy framework
- **Spring Boot** - Web framework
- **React** - UI framework

## 📧 Contact

For questions, suggestions, or discussions about AI-driven development:

- **Issues**: [GitHub Issues](https://github.com/yourusername/newssite/issues)
- **Discussions**: [GitHub Discussions](https://github.com/yourusername/newssite/discussions)

---

**Built with ❤️ and 🤖 AI**

*This README was collaboratively written by humans and AI, demonstrating the same principles as the project itself.*
