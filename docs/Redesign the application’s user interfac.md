Redesign the application’s user interface to create a polished “Editorial Magazine + Language Learning” experience.

## Objective

The current interface is functional but visually basic and inconsistent. Redesign it so that it feels like a premium French news-reading application combined with a focused language-learning tool.

The application should look sophisticated, calm, readable, and modern—not childish or overly gamified.

Before changing anything:

1. Inspect the existing project structure, UI framework, navigation, themes, reusable components, and state management.
2. Identify every existing screen and all current user-facing functionality.
3. Reuse the current architecture and component patterns where reasonable.
4. Do not rewrite working business logic merely to support the redesign.
5. Preserve all existing features, navigation routes, user data, dictionary integrations, reading tools, and vocabulary-review behavior.

## Design Direction

Combine these two visual ideas:

- A premium digital magazine with strong editorial typography, photography, generous spacing, and elegant visual hierarchy.
- A language-learning dashboard with subtle progress indicators, vocabulary levels, review reminders, and reading statistics.

The editorial experience must remain dominant. Learning information should be useful and motivating without making the app look like a game.

## Visual Identity

### Color palette

Create semantic design tokens rather than hardcoding colors inside individual components.

Suggested light theme:

- Background: warm ivory or soft paper tone
- Primary text: carbon black
- Secondary text: warm dark gray
- Primary accent: oxblood or deep editorial red
- Secondary accent: muted teal
- Progress/accent highlight: restrained amber
- Cards: warm white with subtle tonal separation
- Dividers: soft warm gray

Suggested dark theme:

- Background: warm near-black, not pure black
- Surfaces: layered charcoal tones
- Primary text: warm off-white
- Secondary text: muted light gray
- Primary accent: softened editorial red
- Secondary accent: desaturated teal
- Borders and dividers: subtle low-contrast gray

Do not use purple as the dominant color. Avoid neon colors, excessive gradients, heavy shadows, or glassmorphism.

### Typography

Use two complementary typography roles:

- An elegant, highly readable serif typeface for article headlines, section titles, and important editorial content.
- A clean humanist sans-serif typeface for navigation, buttons, metadata, settings, labels, and progress information.

French accents and Persian text must render correctly.

Maintain a clear type scale for:

- Screen titles
- Section headings
- Article headlines
- Body text
- Metadata
- Labels and captions

Reading text must have comfortable line height, reasonable line length, and adequate margins.

## Global UI System

Create or consolidate reusable components for:

- App bars
- Search fields
- Section headers
- News cards
- Article-list items
- Progress indicators
- Difficulty badges
- Category badges
- Review cards
- Vocabulary cards
- Empty states
- Bottom navigation
- Buttons and icon buttons
- Chips and segmented controls
- Dialogs and bottom sheets
- Settings groups

Standardize:

- Spacing
- Corner radii
- Icon sizes
- Touch-target sizes
- Typography
- Card padding
- Borders
- Elevation
- Component states

Use an 8-point spacing system where compatible with the existing framework.

All interactive elements should provide appropriate pressed, selected, focused, disabled, and loading states.

## Home Screen

Substantially redesign the Home screen rather than simply changing colors.

### Header

Add a compact editorial header with:

- Application title: “Lire en français”
- Search action
- Settings action
- Optional short subtitle, provided it does not create clutter

Avoid placing too many unrelated icons in the top app bar.

### Search

Use a wide, visually refined search field with text similar to:

“Rechercher un sujet, un mot, un article…”

The search field should be easy to find but should not dominate the entire screen.

### Learning summary

Below the search field, add one compact learning-summary strip containing useful information such as:

- Reading streak
- Number of words learned
- Weekly goal progress

Example:

- “7 jours”
- “124 mots appris”
- “68 % de l’objectif”

This must remain compact. Do not turn the top of the Home screen into a large statistics dashboard.

If these values are unavailable in the existing data model, use only information that can be derived reliably. Do not add fake persistent statistics.

### Today’s News carousel

Preserve and improve the existing “Today’s News” feature.

This section is important and must remain horizontally scrollable.

Requirements:

- Use a horizontal swipe carousel.
- Display one prominent card and part of the adjacent card so users immediately understand that the row can be swiped.
- Preserve smooth horizontal scrolling.
- Add snapping behavior if supported cleanly by the existing framework.
- Use pagination indicators only when they accurately reflect carousel position.
- Use immersive article photography.
- Place headlines over a controlled image gradient or in a clearly separated text area.
- Show the news category.
- Show the source.
- Show estimated reading time.
- Show an optional language level such as A2, B1, or B2 when that information exists or can be determined reliably.
- Keep text readable over every image.
- Handle missing images with a deliberate editorial placeholder.

The carousel must remain responsive and should not interfere with vertical scrolling.

### Daily review

Add a clear but compact “Révision du jour” card.

It may contain:

- Number of words due for review
- A short explanatory line
- One primary action such as “Commencer”

If nothing is due, show a calm completed state instead of a disabled-looking empty rectangle.

### Continue Reading

Present the current article as a refined bookmark-style card containing:

- Thumbnail
- Article title
- Category or source
- Reading-progress bar
- Percentage completed
- Estimated remaining time
- Difficulty level, when available
- A clear Resume action

This section should be visually important but smaller than the main news carousel.

### My Texts

Redesign saved/imported texts as compact editorial cards or a clean magazine-style list.

Each item can show:

- Thumbnail
- Title
- Source or date
- Estimated reading time
- Vocabulary count or difficulty, when available
- Overflow menu for secondary actions

Avoid oversized rows with large unused spaces.

### Bottom navigation

Keep the principal destinations clear:

- Home
- Library
- Add text

Include another destination only if it already exists and is important enough to be part of primary navigation.

Use consistent icons and labels. The selected destination should be obvious without using an oversized background capsule.

## Library Screen

Redesign the Library as an editorial collection rather than a plain database list.

Include:

- Clear screen title
- Search by title or source
- Sorting and filtering controls
- Consistent article thumbnails
- Title, source, date, difficulty, and reading status where available
- A suitable overflow menu for destructive or secondary actions

Do not show a permanently exposed delete icon on every row unless this is consistent with the platform’s interaction conventions.

Prefer an overflow menu, swipe action, or selection mode with confirmation for deletion.

Create polished states for:

- Loading
- Empty library
- No search results
- Error
- Populated library

The empty state should include:

- A simple editorial illustration or icon
- A helpful explanation
- A primary action such as “Ajouter un texte”

## Reading Screen

The reading experience is the most important part of the application.

Create a distraction-free editorial reading layout.

### Reading area

- Use a warm paper background in light reading mode.
- Use a warm near-black background in night reading mode.
- Apply comfortable horizontal margins.
- Use excellent line height and paragraph spacing.
- Avoid lines that are too wide.
- Preserve French punctuation and accents correctly.
- Support existing font-size and reading-background preferences.
- Keep reading settings independent from the general application theme if that is already supported.

### Reading toolbar

Simplify and visually organize the existing reading tools.

Group related actions and use consistent iconography for:

- Back
- Text settings
- Reading progress or page indicator
- Saved vocabulary
- Article information
- Text-to-speech
- Translation
- Other existing tools

Do not remove any working reading feature.

Secondary actions can move into an overflow menu if the toolbar is currently overcrowded.

### Progress

Show subtle reading progress through one of the following:

- A thin progress bar
- A compact page indicator
- A small percentage indicator

Avoid making progress controls visually compete with the article.

## Dictionary Bottom Sheet

Redesign the selected-word dictionary as a polished bottom sheet.

The bottom sheet should:

- Use a rounded top edge.
- Support drag-to-expand and drag-to-dismiss if already compatible with the project.
- Clearly display the selected word.
- Include pronunciation or text-to-speech.
- Show the short Persian meaning.
- Allow editing the meaning if currently supported.
- Show the selected vocabulary list or category.
- Provide a clear save action.
- Show whether the word is already saved.
- Preserve WordReference, Larousse, Linguee, Reverso, or any existing dictionary integrations.
- Keep embedded web dictionary content visually separated from native controls.

Suggested hierarchy:

1. Selected word
2. Pronunciation and save controls
3. Persian meaning
4. Vocabulary category
5. Dictionary-source tabs
6. Embedded dictionary content

Avoid giving every action the same visual weight.

Ensure the keyboard does not cover editable fields or the save action.

## Saved Vocabulary Screen

Redesign this screen around review and vocabulary organization.

### Header and filtering

Include:

- “Saved Vocabulary” title
- Search
- Visibility or display options, if currently supported
- Horizontally scrollable list/category chips
- “New list” action

Selected and unselected chips must be clearly distinguishable.

### Review card

Create an attractive review card showing:

- Number of words ready
- Optional daily progress
- A clear action to start reviewing

Provide visually distinct states for:

- Words ready to review
- Review completed
- No scheduled words

### Vocabulary items

Display each vocabulary entry in a structured card containing:

- French word
- Example sentence
- Persian meaning with correct RTL alignment
- Vocabulary list/category
- Review status
- Learned state
- Context/source when available
- Secondary actions in an overflow menu

Do not place the delete action prominently beside the primary learning state. Destructive actions should require confirmation.

### Empty state

Center the empty state within the available content area.

Do not place a long sentence at the bottom edge of an otherwise empty screen.

Use:

- A simple illustration or icon
- “No words saved yet”
- A short explanation
- A clear action or instruction directing users to the Reading screen

## Settings Screen

Replace the long uninterrupted list of radio buttons with structured settings groups.

Possible groups:

- General
- Interface language
- Appearance
- Reading
- Text-to-speech
- Dictionary and translation
- Vocabulary review
- About

Use appropriate controls:

- Segmented control or selection dialog for mutually exclusive options
- Switches for binary settings
- Sliders or previews for font size and line spacing
- Small visual theme previews where useful

Clearly separate:

- Application theme
- Reading background
- Reading font
- Reading font size
- Line spacing

Show the effect of reading-related settings through a small live text preview if it can be implemented without unnecessary complexity.

## RTL and Localization

The application contains Persian content, so support mixed LTR and RTL layouts correctly.

Requirements:

- Persian definitions must use correct RTL alignment.
- French words and sentences must remain LTR.
- Mixed-language components must not reorder punctuation incorrectly.
- Do not force the entire screen into RTL merely because one field contains Persian.
- All new user-facing strings must use the project’s localization system.
- Do not leave hardcoded English strings in localized screens.
- Preserve current interface-language options.

## Motion and Interaction

Use subtle, purposeful motion:

- Smooth carousel scrolling and snapping
- Gentle card press feedback
- Short bottom-sheet transitions
- Animated progress changes
- Crossfade or shared transitions only where supported cleanly
- Respect the operating system’s reduced-motion preference

Avoid decorative or slow animations.

## Accessibility

Ensure:

- Accessible color contrast in light and dark themes
- Minimum practical Android touch targets
- Screen-reader labels for icons
- Logical focus order
- Text scaling support
- No important meaning communicated by color alone
- Clear selected, focused, disabled, and error states
- Readable text over photographs
- Proper RTL accessibility for Persian meanings

## Responsive Behavior

The interface should work on:

- Small Android phones
- Typical modern phones
- Larger phones
- Different text-scaling settings

Avoid fixed heights that clip translated text.

Long French titles must wrap or truncate intentionally without breaking the layout.

Horizontal carousels must not cause the entire screen to overflow horizontally.

## Technical Constraints

- Preserve the existing application architecture unless a small targeted refactor is necessary for reusable styling.
- Do not replace the project’s UI framework.
- Do not introduce a large dependency only for visual polish.
- Prefer reusable theme tokens and components over screen-specific styling.
- Preserve existing APIs, storage, navigation, dictionary integrations, and reading behavior.
- Do not use fake data in production paths.
- Do not remove existing functionality without explicitly reporting it.
- Keep the redesign maintainable and consistent with platform conventions.

## Implementation Process

Proceed in this order:

1. Audit the existing screens and reusable UI components.
2. Identify duplicated styles and inconsistent UI patterns.
3. Define the new color, typography, spacing, shape, and elevation tokens.
4. Implement or update reusable primitives.
5. Redesign the Home screen first.
6. Verify the Home screen before applying the system to the remaining screens.
7. Redesign Library, Reading, Dictionary, Saved Vocabulary, and Settings.
8. Add polished loading, empty, error, and populated states.
9. Verify light mode, dark mode, reading themes, localization, and RTL content.
10. Run formatting, static analysis, tests, and the existing build process.
11. Fix regressions caused by the redesign.

## Acceptance Criteria

The redesign is complete when:

- The interface is visibly and structurally different from the current basic design.
- The Home screen successfully combines an editorial magazine with restrained learning progress.
- Today’s News remains a smooth horizontal carousel.
- A neighboring news card is partially visible to communicate swipe behavior.
- Reading remains comfortable and distraction-free.
- Dictionary interactions and external dictionary sources still work.
- Persian meanings display correctly in RTL.
- Saved vocabulary and daily review are easier to understand.
- Empty states are centered, helpful, and actionable.
- Settings are grouped and easier to scan.
- Light and dark themes are visually coherent.
- Existing functionality and stored user data remain intact.
- The application builds successfully and relevant tests pass.

Treat the supplied mockups as visual direction, not pixel-perfect specifications. Adapt the design intelligently to the existing technology, data, navigation, and platform conventions. If a proposed element requires unavailable data, omit or simplify it rather than inventing misleading information.