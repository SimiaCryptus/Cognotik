import { TaskTypeTest } from '../types';
/**
 * Predefined task type configurations for testing various capabilities
 * of the Cognotik AI platform.
 */
/**
 * Code Generation Task
 * Tests the ability to generate code based on user requirements
 */
export declare const codeGenerationTask: TaskTypeTest;
/**
 * Data Analysis Task
 * Tests the ability to analyze data and provide insights
 */
export declare const dataAnalysisTask: TaskTypeTest;
/**
 * Document Summarization Task
 * Tests the ability to summarize long documents
 */
export declare const documentSummarizationTask: TaskTypeTest;
/**
 * Image Generation Task
 * Tests the ability to generate images based on text descriptions
 */
export declare const imageGenerationTask: TaskTypeTest;
/**
 * Content Rewriting Task
 * Tests the ability to rewrite content in different styles
 */
export declare const contentRewritingTask: TaskTypeTest;
/**
 * Translation Task
 * Tests the ability to translate content between languages
 */
export declare const translationTask: TaskTypeTest;
/**
 * Map of all available task types for easy lookup
 */
export declare const taskTypes: Record<string, TaskTypeTest>;
/**
 * Get a task type configuration by ID
 * @param taskId The ID of the task type to retrieve
 * @returns The task type configuration or undefined if not found
 */
export declare function getTaskTypeById(taskId: string): TaskTypeTest | undefined;
/**
 * Get all available task types
 * @returns Array of all task type configurations
 */
export declare function getAllTaskTypes(): TaskTypeTest[];
