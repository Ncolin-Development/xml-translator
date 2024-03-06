# XML-Translator

## State
Reads all files from target, finds the one with .fr. in their name and try to fill them up using deepl and the .es. and en files.
Doesn't work as a standalone jar yet

## How to
- Place files in target folder (fr, en and es)
- Start service
- See that a .fr.resx.translated has been generated with all translations in it

## Problems
- If it fails, nothing is written, your deepl's character count doesn't go down --> Should physically write results as soon as its translated instead of in the inputstream. This would allow you to see progress and protect you against unforeseen errors
- Didn't take the time to properly make this a service. You have to start the project to make it run as the translation happen in PostConstruct of the XmlParser... (yup)