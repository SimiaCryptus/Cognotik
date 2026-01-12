import unittest
import subprocess
import sys
import os
class TestCouncilGreeting(unittest.TestCase):
    EXPECTED_OUTPUT = "Hello from CouncilMode"
    def test_hello_output(self):
        """Verify that hello.py prints the correct greeting."""
        script_path = 'hello.py'
        # Ensure the script exists
        self.assertTrue(os.path.exists(script_path), f"{script_path} does not exist")
        # Run the script and capture output
        result = subprocess.run([sys.executable, script_path], capture_output=True, text=True)
        output = result.stdout.strip()
        # Check if output matches expected string
        self.assertEqual(output, self.EXPECTED_OUTPUT)
if __name__ == '__main__':
    unittest.main()