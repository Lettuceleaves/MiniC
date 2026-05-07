# UI API source layout

The Java package remains `minic.uiapi` for compatibility. Files are grouped by API responsibility:

- `api`: public facade classes used by UI clients.
- `core`: common DTOs shared by compiler observation and debugger views.
- `visual`: compiler pipeline visual DTOs and builders.
- `debug`: debugger DTOs, mappers, and view builders.
