package cognotik.actions.plan

/**
 * Version of UnifiedPlanAction that always creates a temporary directory,
 * ignoring any selected files or folders in the project view.
 * This is intended for use from the main menu.
 */
class UnifiedPlanFromMenuAction : UnifiedPlanAction(useProjectRoot = false)