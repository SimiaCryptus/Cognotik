---
transforms:
  - (.*)/apps/(.*)/app\..* -> $1/apps/$2/icon.png
  - (.*)/apps/(.*)/PIPELINE\.md -> $1/apps/$2/icon.png
task_type: GenerateImage
---

Create an icon image for the app.

* The image should be visually appealing and relevant to the app's purpose.
* It should be designed in a way that it can be used as an icon for the app, providing a visually engaging representation of the app's functionality and purpose. 
* The image should be high-quality and optimized for web use, ensuring that it loads quickly and looks good on various devices and screen sizes.
