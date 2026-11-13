# DriverFin - UI (visual system)

Theme: LIGHT (beige + white). `theme-dark.css` has a historical name; MainApp references it, so it was not renamed.
Colors are looked-up colors on `.root` (`-c-*`), defined once in `src/main/resources/com/driverfin/css/theme-dark.css`.
Ratios are WCAG 2.x, computed with the formula (not by eye). Backgrounds: white #fff, beige #e3dccf (root and the end of the KPI gradient), calc block #d8e0d5.

## Palette
| Name | Value | White | Beige | Calc #d8e0d5 | Use |
|---|---|---|---|---|---|
| -c-bg | #e3dccf | | | | window background |
| -c-surface | #ffffff | | | | cards, fields, dialog |
| -c-surface-alt | #faf9f6 | | | | odd rows, quiet buttons |
| -c-text | #4a4a4a | 8.86 | 6.50 | 6.56 | main text |
| -c-muted | #595959 | 7.00 | 5.14 | 5.18 | KPI titles, subtitle, captions, prompts, `text-muted` |
| -c-revenue | #3d6166 | 6.77 | 4.97 | 5.01 | faturamento (teal) |
| -c-profit | #4a6648 | 6.39 | 4.69 | 4.73 | lucro (green) |
| -c-cost | #9a4529 | 6.45 | 4.73 | 4.77 | custos (terracotta) |
| -c-maint | #85560f | 6.30 | 4.62 | 4.66 | manutencao (amber) |
| -c-uber | #1e4a9a | 8.40 | 6.16 | 6.21 | form label Uber |
| -c-99 | #0a6285 | 6.78 | 4.97 | 5.02 | form label 99 |
| -c-fuel | #8a4b08 | 6.79 | 4.98 | 5.03 | form label combustivel (orange-brown, not red) |
| -c-food | #6e4630 | 8.13 | 5.97 | 6.02 | form label alimentacao (brown) |
| -c-prompt | #767676 | 4.54 | | | placeholder in white fields only |
| -c-scroll-thumb | #7a7463 | | 3.42 | 3.45 | scroll bar thumb on beige track (>= 3:1) |
| -c-border | #8a8473 | 3.73 | 2.74 | | field/button borders (white contexts only, >= 3:1) |
| -c-focus | #3d6166 | 6.77 | 4.97 | | focus ring (>= 3:1) |
| -c-error | #c62828 | 5.62 | 4.12 | | invalid field border (>= 3:1) |

Filled backgrounds with white text (-c-on-dark #fff): primary #4a6648 = 6.39, primary hover #3b5539 = 8.25, secondary #3d6166 = 6.77, hover #2f5156 = 8.63, danger #9a4529 = 6.45, hover #8a3a20 = 7.76, table/combo/date selection #3f6469 = 6.49.
Before -> after (worst cases): receita bruta 2.0 -> 4.97 (beige); maintenance 2.3 -> 6.30; lucro 2.4 -> 4.69; custos 2.6 -> 4.73; faturamento medio 2.1 -> 4.97; titles/subtext 3.1/3.5 -> 5.14; primary button 3.3 -> 6.39; secondary 2.8 -> 6.77; field focus 2.8 -> 6.77.

## One color family per idea (cards, chart, form)
- FATURAMENTO = teal: `kpi-value-gross`, `kpi-value-gross-rate`, `calc-value-gross`, chart series 0, `btn-secondary`.
- LUCRO = green: `kpi-value-net`, `kpi-value-profit-rate`, `kpi-value-efficiency` (compat), `calc-value-net`, chart series 2, `btn-primary`.
- CUSTOS = terracotta: `kpi-value-costs`, `calc-value-costs`, chart series 1, `btn-danger`.
- MANUTENCAO = amber: `kpi-value-maint`, `calc-value-maint`.
Purple and sky blue were removed.

## Typography and spacing
Font Segoe UI. Sizes: 22 (title-label), 20 (KPI value), 15 (section-header), 13 (body), 11-12 (kpi-title, subtext, captions, btn-sm). Spacing scale 4, 8, 12, 16, 24 (card padding 16, button 8x16, field 8x12).

## Classes
- Text: `title-label`, `subtitle-label`, `section-header`, `text-muted` (secondary text: "Periodo:", legends).
- Cards: `card`, `kpi-card`, `kpi-title`, `kpi-value-gross|gross-rate|costs|maint|net|profit-rate|efficiency`, `kpi-subtext`.
- Form: `form-label` (default, bold), `form-label-uber|99|fuel|food`, `text-field` + `input-error` (red 2px border; also add a text message, color is not the only signal).
- Calc block (#d8e0d5): `calc-caption` (small legend), `calc-value` (neutral bold), `calc-value-gross|costs|maint|net`.
- Buttons: `button` (generic, quiet: dark text, light fill, e.g. table Edit/Excluir and dialog buttons), `btn-primary` (one per screen), `btn-secondary`, `btn-outline`, `btn-danger`, `btn-sm` (combine with others).
- Table: selected row = #3f6469 with white text. Dialogs: `.dialog-pane` styled; default button looks like primary.
- Root: `root-pane` (beige) for the form root.

## States
Hover darkens fill; pressed same as hover; focused = 2px ring (teal on generic button and fields, dark text color on colored buttons); disabled = 50-60% opacity. Error = red border.

## Not validated (no screen seen, build only)
- Looked-up colors inside popups (combo list, date picker) and dialogs: expected to resolve, but check visually.
- Chart legend symbol selectors (`.chart-legend-item-symbol.default-colorN`) and bar colors if series are colored in code.
- Disabled-state opacity is exempt from contrast rules but check it is still readable.
- Gradient text on `kpi-card` was checked at both ends (white and beige); mid values lie between.
- #3d6166 focus ring on beige is 4.97; the ring on white-field borders is fine. Red error border on beige is 4.12 (>= 3).

## T11: data entry and dashboard S-1 to S-4 (new classes)
New colors on `.root`: `-c-error-text` #a61e1e (error TEXT: white 7.43, beige 5.45, calc 5.50; `-c-error` #c62828 stays for borders only because it is 4.12 on beige) and `-c-warn-bg` #fdf1d6 (warning fill; `-c-maint` text on it = 5.62, `-c-text` on it = 7.90).
Verified by loading the CSS in a JavaFX program: every class below resolved to the expected text fill (no gray #4a4a4a fallback).

| Class | Use | Style / contrast |
|---|---|---|
| `money-hint` | fixed hint above the first money field | 12px, `-c-muted` (5.14 on beige) |
| `input-affix` | "R$" before and "km" after a field | 13px bold `-c-muted` |
| `field-error` | message under/next to a field; add `input-error` to the field and start the text with a mark (e.g. "! ") | 12px bold `-c-error-text` |
| `field-warning` | soft, non-blocking warning and midnight notice; put a warning sign in the text | amber text, `-c-warn-bg` fill, amber 1px border; 5.62 |
| `field-info` | neutral hint (suggested KM, automatic comma) | 11px `-c-muted` |
| `calc-block` | container of the calculation (VBox/HBox) | `-c-calc-bg`, radius 12, padding 12x16 |
| `calc-value-large` | add to `calc-value-gross` (revenue) or `calc-value-net` (profit); gives 22px bold, color comes from the pair class | 4.69+ on #d8e0d5 |
| `today-card` | S-1 container (white card) | same look as `card` |
| `today-title` | "HOJE - date" | 12px bold muted |
| `today-profit` | profit of today, largest text (32px, green 6.39 on white) | |
| `today-profit-negative` | replaces `today-profit` on a loss (terracotta 6.45); the text must also say "(prejuizo)" | |
| `today-metric-label` / `today-metric-value` | small caption / 20px value (hours, revenue per hour) | muted / text |
| `today-empty-text` | "no shift today" variant: 15px normal text, shown instead of zero values | `-c-text` |
| `empty-state` | S-2 box (VBox): white, radius 16, padding 32, centered | |
| `empty-state-title` / `empty-state-text` | 22px bold / 15px muted | |
| `btn-primary` inside `.empty-state` | automatically larger (15px, padding 12x32); no extra class needed | |
| `shortcut-hint` | discreet 11px hint below the table | muted |
| `maint-help` | always-visible explanation of the maintenance rate, 12px | muted |

Ambiguities settled: the midnight notice uses `field-warning`; "no shift today" is the same `today-card` with `today-empty-text` (no extra class); error colors are never the only signal (needs `!` text + red border).
Focus: unchanged and consistent (2px teal ring on fields/table, dark ring on colored buttons). Not checked visually.

## Review fixes (D2, D4, D5, D6, D7) - rendered and checked
- D2 placeholder: `-c-prompt` #767676 (4.54 on white) for text-field, text-area, date-picker and editable combo editors. Typed text stays `-c-text` #4a4a4a (8.86). Italic is NOT possible: JavaFX CSS has no separate font for the prompt (it would also italicize typed text). The difference is color only.
- D4 loss: add class `value-negative` next to the value class when value < 0. Compound selectors force `-c-cost` (#9a4529: white 6.45, beige 4.73, calc 4.77). Covers `kpi-value-*`, `calc-value*`, `today-profit`, `today-metric-value` and `.table-cell.value-negative` (a selected row stays white on #3f6469). Also write "-" or "(prejuizo)" in the text. Rendered: textFill = #9a4529 for every combination tested.
- D5 calc block: `calc-block` padding 16; caption and values centered on one axis. Title uses `section-header` (java-dev).
- D6 DatePicker / combo: the outer control has the only outline; the inner editor has no border, no background, no padding of its own (8x12 inside); calendar icon is inside the outline; 2px teal focus ring on the outer control rendered and visible. TextArea: inner scroll-pane and content are transparent, no weak top line.
- D7 form-label-fuel #8a4b08 and form-label-food #6e4630 replace the red/terracotta hues (fuel vs `-c-error-text` ratio 1.09, so visually distinct). Red now means error only.
- Scroll: `.scroll-pane` transparent, no border, no padding; thumb #7a7463 (3.42 on beige track); bar padding 2.
- `form-actions`: apply to the HBox of the form buttons. Thin 1px `-c-border-soft` line above, padding-top 12, spacing 8, aligned right.
- Buttons: btn-primary and btn-secondary keep white text (6.39 / 6.77); btn-outline keeps dark text. Rendered primary + outline side by side: fine.

## Fixed form footer, dashboard finishing (rendered with a JavaFX test program; PNG checked)
Contrast computed with a script (WCAG formula). New pairs:
| Pair | Ratio | Rule |
|---|---|---|
| `-c-maint` #85560f text on `-c-calc-bg` #d8e0d5 | 4.66 | field-warning inside the strip (its own fill is `-c-warn-bg`: 5.62) |
| `-c-muted` on #d8e0d5 | 5.18 | calc-caption in the strip |
| `-c-text` on #d8e0d5 | 6.56 | calc-value |
| `-c-error-text` on #d8e0d5 | 5.50 | |
| `-c-border` #8a8473 on #d8e0d5 / on beige | 2.76 / 2.74 | FAILS 3:1 for borders, so it is no longer used on beige or green-grey |
| `-c-muted` #595959 as outline border: beige / #d8e0d5 | 5.14 / 5.18 | new `btn-outline` border |
| `-c-profit` #4a6648 on beige | 4.69 | Novo Turno text and border |
| table menu arrow `-c-muted` on beige | 5.14 | icon needs >= 3:1 |
| scroll thumb #7a7463 on beige | 3.42 | |
| `-c-border-soft` line on #d8e0d5 | 1.22 | decorative only; the strip is separated by its fill, not by the line |

1. `calc-strip` (new): HBox/VBox, fill `-c-calc-bg`, 1px `-c-border-soft` line on top, padding 10x16, spacing 24, centered. Inside it: `calc-caption` (11px), `calc-value*` (14px bold), `calc-value-large` (20px); `field-warning` wraps, max width 528 (fits 560 minus padding). Remember to keep the warning labels managed=false when hidden.
2. `form-actions` changed: no top line, same fill `-c-calc-bg`, padding 8 16 12 16. The line belongs to `calc-strip` only, so strip + buttons read as one footer with no double line. The strip and the bar must be direct neighbours with no gap or own padding/background in the parent (the current `<bottom>` HBox has Insets 24/12 set in FXML: the class overrides it; remove the Insets).
3. `btn-outline` changed: border is `-c-muted` (was `-c-border`, 2.74 on beige: failed 3:1); hover border `-c-text`.
4. Header buttons: Backup = neutral outline (gray border/dark text); Novo Turno = `btn-outline` with CSS id `#btnNewRecord` (already in FXML): green text and border (`-c-profit`, same family as the primary action), hover `-c-profit-dark`. Relatório PDF stays filled teal. Three distinct looks. If the id changes, update the selector `#btnNewRecord.btn-outline`.
5. Chart legend: hgap 24, vgap 8, padding 8x16, symbol and text gap 8, rounded symbol, items centered in their tile (JavaFX gives every legend tile the width of the widest label, so left aligned short labels looked glued to the next one). Symbol colors unchanged: teal / terracotta / green (verified in render).
6. Table: column menu button `.show-hide-columns-button` transparent, arrow `-c-muted` (5.14), larger (padding 5x6), hover/focus background `-c-border-soft` and arrow `-c-text`; `.corner` beige; horizontal bar thumb 3.42 with padding 3x2.
7. Card HOJE: padding 16 -> 12x16; `.today-card .text-muted` 13px with no padding (summary line); `today-metric-label` 11 -> 12px (muted on white 7.00).
Not checked: the real screens (only a test program with the same classes). Check: footer with the 4 values + warning at 560px, header buttons, legend, table menu icon.
