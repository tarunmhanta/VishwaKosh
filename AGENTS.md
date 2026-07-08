# AI Agent Guidelines

## Design Theme: Professional Polish
This application implements the **Professional Polish** Material 3 visual identity, adhering strictly to clean colors, balanced negative space, and modern typographic hierarchy.

### Core Color Palette
- **Primary Color:** Deep Purple (`#6750A4`) representing the premium, focused backup experience.
- **Secondary Color:** Soft Lavender Slate (`#625B71`) for subtle, accessible secondary elements.
- **Tertiary Accent:** Warm Rose (`#7D5260`) for active states and notification triggers.
- **Background Tint:** Soft, eye-safe Warm Beige/Lavender (`#FDF8F6`) in light theme to reduce visual strain.
- **Dark Mode Alternative:** Obsidian Slate (`#141218`) with high-contrast pastel accents for low-light support.

### Visual Architecture & Spacing
- Use Material Design 3 container curves with standard `8.dp` grid padding.
- Emphasize touch ergonomics with a minimum target size of `48.dp` on all interactive buttons and chip filters.
- Use explicit Material 3 color roles (`primaryContainer`, `onPrimaryContainer`) instead of hardcoded hex values in Composable views.
