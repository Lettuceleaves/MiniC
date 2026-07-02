# Pipeline Layout Controls Design

Date: 2026-06-26
Status: Approved for implementation

## Goal

The compile pipeline workbench should give the central before/after/code content more usable space while preserving quick access to compiler controls and metadata. The layout must support a movable compiler control module, collapsible sidebars, and a reversible two-column workspace split.

## Current Structure

The local JavaFX workbench currently renders the CODE view as:

- left pipeline stage sidebar
- center editor area containing the workspace split and bottom panel
- right inspector containing compiler controls and metadata

The UIWeb mirror follows the same structure. The existing workspace split only models "right" tab ids, so a tab can be split right but there is no symmetric move back. The right group can also remain even when it should collapse.

## Target Layout

The CODE view becomes:

- activity bar
- left pipeline sidebar, collapsible
- central workspace split, containing at most two tab groups: left and right
- right metadata sidebar, collapsible
- compiler control module, dockable or floating

The compiler control module contains only the compiler control buttons:

- next
- next stage
- run to execution
- play
- 2x play
- pause

The metadata sidebar keeps the current state, current item, and accumulated output.

## Compiler Control Placement

The compiler control module has exactly three placement modes:

- `RIGHT_METADATA_TOP`: embedded at the top of the right metadata sidebar.
- `LEFT_PIPELINE_BOTTOM`: embedded below the left pipeline stage list.
- `FLOATING`: shown as a floating panel inside the central workspace split.

Only one placement mode is active at a time. Moving the control module does not reset pipeline session state, selected stage, tab split state, or scroll state.

## Floating Boundary And Coordinates

When `consoleDock` is `FLOATING`, the floating panel is constrained to the central workspace split: the common parent of the before and after workspace groups.

The floating rectangle is persisted relative to that workspace split's top-left corner:

```json
{
  "x": 24,
  "y": 24,
  "width": 320,
  "height": 120
}
```

The rectangle is not relative to the full pipeline page, app window, screen, left sidebar, or right sidebar. Dragging and resizing clamp the rectangle inside the workspace split. If the workspace split becomes smaller, the stored rectangle is kept but the rendered rectangle is clamped to fit.

## Sidebar Collapse

The left pipeline sidebar and right metadata sidebar can each collapse and expand independently.

Collapsed sidebars keep a narrow rail with an expand button. Collapse must not destroy component state. Expanding should restore the previous content without changing the selected stage, active tab, control placement, or pipeline progress.

## Workspace Tab Split Model

The workspace supports at most two tab groups: left and right.

Each workspace tab belongs to either the left group or the right group. The UI provides:

- split right: move the tab to the right group and focus it there
- move left: move the tab back to the left group and focus it there
- close: close a stage tab or source document according to the existing rules

If the right group becomes empty, it disappears.

If the left group becomes empty, all right-group tabs move back to the left group. The active tab from the previous right group becomes the active left tab. This prevents the workspace from ending in a blank or unreachable left column.

The pipeline before and after tabs remain document-level reusable tabs. Starting a pipeline creates or reuses one before tab and one after tab. Pipeline stage changes update the displayed content inside these tabs; they do not create stage-specific tab pairs.

## Persistence

The layout state is global and persisted in settings. It includes:

- `pipelineLeftSidebarCollapsed`
- `pipelineRightSidebarCollapsed`
- `compilerControlsDock`
- `compilerControlsFloatingRect`
- `rightWorkspaceTabIds`
- `activeLeftWorkspaceTabId`
- `activeRightWorkspaceTabId`

Persisted tab ids are best-effort. If a persisted id no longer exists after reopening files, the shell falls back to the active document's source tab. Invalid right-tab ids are ignored.

## JavaFX Architecture

Introduce a small layout state layer in the JavaFX workbench shell:

- centralize dock state, sidebar collapse state, and workspace split state
- render the compiler controls through a reusable control component instead of embedding them only inside `MiniCInspectorView`
- keep `MiniCInspectorView` focused on metadata
- keep the existing performance fix: normal pipeline progress must not rebuild the whole workbench or workspace split unless tab layout actually changes

The floating compiler controls should be hosted above the workspace split content, with coordinates measured against the workspace split container.

## UIWeb Architecture

Mirror the same layout state and component split in UIWeb:

- reusable compiler controls component
- metadata-only inspector component
- dock/floating rendering paths
- two-group tab model with split-right and move-left actions
- persisted settings fields matching the JavaFX names

The UIWeb shell should avoid full-shell re-renders for routine pipeline state changes. Only layout-state changes should update the shell-level layout.

## Verification

Add regression coverage for:

- compiler controls can render in right metadata, left pipeline, and floating modes
- floating rectangle is clamped relative to the workspace split
- collapsing and expanding each sidebar preserves pipeline state
- split-right moves a tab to the right group
- move-left moves a tab back to the left group
- empty right group disappears
- empty left group causes all right tabs to move back left
- next and tab switching do not rebuild expensive workspace content unnecessarily

Run the existing Java and UIWeb strict verification after implementation.
