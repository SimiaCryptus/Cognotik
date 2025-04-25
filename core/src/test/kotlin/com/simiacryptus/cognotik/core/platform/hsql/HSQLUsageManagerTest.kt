import com.simiacryptus.cognotik.platform.hsql.HSQLUsageManager
import com.simiacryptus.cognotik.platform.test.UsageTest
import org.junit.jupiter.api.TestInstance
import java.nio.file.Files

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HSQLUsageManagerTest : UsageTest(HSQLUsageManager(Files.createTempDirectory("usageManager").toFile()))

