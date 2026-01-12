def createPythonScript(String fileName, String message) {
    def config = new com.simiacryptus.cognotik.plan.tools.file.FileModificationTask$FileModificationTaskExecutionConfigData()
    config.files = [fileName]
    config.task_description = "Create a simple python script named ${fileName}"
    
    return FileModification.call(config, message)
}

createPythonScript("hello.py", "Create a simple python script that prints 'Hello from CodingMode'")
