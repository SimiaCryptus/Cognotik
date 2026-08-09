if (homeEnabled) {
register(context, ServletHolder("home", StaticResourceServlet()), HOME_PREFIX)
/* The homepage's own control surface: server description + model selection. */
register(context, ServletHolder("settings-api", SettingsApiServlet()), SETTINGS_PREFIX)
register(context, ServletHolder("user-settings", UserSettingsServlet()), "/userSettings")
register(context, ServletHolder("provider-api", ApiProviderServlet()), "/apiProviders")
register(context, ServletHolder("keys-api", ApiKeyServlet()), "/apiKeys")
}