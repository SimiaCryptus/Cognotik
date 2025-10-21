<#
.SYNOPSIS
    Launches a simple graphical demonstration window with a bouncing object.

.DESCRIPTION
    This script demonstrates how to create a basic GUI application using PowerShell
    and the .NET Windows Forms library. It creates a window with a piece of text
    that bounces off the edges of the window.

    This serves as an example of PowerShell's capabilities beyond simple command-line
    scripting.

.EXAMPLE
    .\scripts\WindowDemo.ps1

    This will run the script from the project root and launch the bouncing text window.
    Close the window to terminate the script.
#>

# --- Setup ---
 # Load the required .NET assemblies for creating a GUI
 try {
    Add-Type -AssemblyName System.Windows.Forms
    Add-Type -AssemblyName System.Drawing
}
catch {
    Write-Warning "Failed to load Windows Forms assemblies. This script requires a Windows environment with .NET Framework."
    exit 1
}

# --- Window and Control Creation ---
# Create the main window (the Form)
$form = New-Object System.Windows.Forms.Form
$form.Text = "PowerShell Bouncing Text Demo"
$form.Size = New-Object System.Drawing.Size(800, 600)
$form.StartPosition = "CenterScreen"
$form.FormBorderStyle = "FixedSingle" # Prevent resizing
$form.MaximizeBox = $false

# Create the object that will bounce (a Label)
$label = New-Object System.Windows.Forms.Label
$label.Text = "PS"
$label.Font = New-Object System.Drawing.Font("Segoe UI", 24, [System.Drawing.FontStyle]::Bold)
$label.ForeColor = [System.Drawing.Color]::DodgerBlue
$label.AutoSize = $true

# Add the label to the form's controls
$form.Controls.Add($label)

# --- Animation Logic ---
# Initial position and velocity
$position = New-Object System.Drawing.Point(50, 50)
$velocity = New-Object System.Drawing.Point(5, 5) # Pixels per tick

# Create a timer to drive the animation
$timer = New-Object System.Windows.Forms.Timer
$timer.Interval = 16 # Roughly 60 FPS (1000ms / 60)

# Define the action to perform on each timer tick
$timer.add_Tick({
    # Update position based on velocity
    $position.X += $velocity.X
    $position.Y += $velocity.Y

    # Get the client area dimensions for collision detection
    $clientWidth = $form.ClientSize.Width
    $clientHeight = $form.ClientSize.Height

    # Check for collision with left or right walls
    if (($position.X -le 0) -or (($position.X + $label.Width) -ge $clientWidth)) {
        $velocity.X = -$velocity.X # Reverse horizontal direction
    }

    # Check for collision with top or bottom walls
    if (($position.Y -le 0) -or (($position.Y + $label.Height) -ge $clientHeight)) {
        $velocity.Y = -$velocity.Y # Reverse vertical direction
    }

    # Apply the new position to the label
    $label.Location = $position
})

# --- Event Handlers and Script Execution ---
# Ensure the timer is stopped and disposed when the form is closed
$form.add_FormClosing({
    Write-Host "Window closed. Stopping animation timer."
    $timer.Stop()
    $timer.Dispose()
})

# Start the animation and show the window
Write-Host "Launching demo window... Close the window to exit." -ForegroundColor Cyan
$timer.Start()
# ShowDialog() makes the window modal and waits for it to close before the script continues/exits.
$form.ShowDialog()

Write-Host "Script finished." -ForegroundColor Green