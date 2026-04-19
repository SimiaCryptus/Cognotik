// Type for markdown-formatted strings that support:
 // - **bold**
 // - *italic*
 // - `code`
 // - [link text](url)
 // - bullet points with - or *
 type MarkdownString = string;

 // More detailed type with markdown support
 interface Resume {
    personal: {
        name: string;
        title?: MarkdownString;
        email?: string;
        location?: string;
        website?: string;
        phone?: string;
        linkedin?: string;
        github?: string;
        socialMedia?: Array<{
            platform: string;
            url: string;
            username?: string;
        }>;
    };

    summary?: MarkdownString;

    coreCompetencies?: MarkdownString[];

    experience?: Array<{
        position: string;
        company: string;
        location?: string;
        startDate?: string;
        endDate?: string;
        employmentType?: 'full-time' | 'part-time' | 'contract' | 'freelance' | 'internship';
        remote?: boolean;
        achievements?: string[];
        technologies?: string[];
        teamSize?: number;
        highlights: Array<MarkdownString | {
            title?: string;
            url?: string;
            type?: string;
            description?: MarkdownString;
        }>;
    }>;

    publications?: Array<{
        name: string;
        url?: string;
        description?: MarkdownString;
    }>;

    skills?: Record<string, Array<string | {
        name: string;
        level?: 'beginner' | 'intermediate' | 'advanced' | 'expert';
        yearsOfExperience?: number;
    }>>;

    projects?: Array<{
        name: string;
        url?: string;
        repository?: string;
        description: MarkdownString;
        technologies?: string[];
        startDate?: string;
        endDate?: string;
        highlights?: MarkdownString[];
    }>;

    education?: {
        institution: string;
        degree: string;
        minor?: string;
    };
    metadata?: {
        version: string;
        lastUpdated: Date;
        targetRole?: string;
        industry?: string;
        keywords?: string[];
        visibility?: 'public' | 'private' | 'unlisted';
    };
}