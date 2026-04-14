# AGENTS.md

Rules for AI agents working on this project.

## Context

ATPEZMS (Theme Park Management System) for a Software Engineering course. Project spec is `ATPEZMS.md`. Course reference documents (SRS, UML, DFD, Data Dictionary) are in `docs_reference/`.

## Education First

The user must be able to justify and explain every line of code, every design decision, and every configuration choice. This is the top priority.

- Explain concepts, annotations, and patterns before or immediately after introducing them.
- Do not hand-wave with "this is standard practice" -- explain *why* it is standard practice.
- If asked to slow down or re-explain, do it. Understanding matters more than speed.

## Process

1. **Waterfall.** Requirements (`ATPEZMS.md`) -> design (`design.md`) -> implementation. Finish and confirm each layer before moving to the next. Do not write code until the design is explicitly approved.
2. **No guessing.** If something breaks, diagnose the root cause. Do not blindly bump versions, add dependencies, or try random fixes.
3. **Ask before acting.** If a decision is ambiguous, ask. This includes technology choices, structural decisions, and scope calls.
4. **Decisions belong in design.** Technology choices, architectural patterns, data models, and workflows go in `design.md`, decided together during the design phase.

5. **Docs are part of the deliverable.** For every non-trivial change:

- Re-read the relevant source docs before coding (at minimum: `ATPEZMS.md`, `DESIGN_RULES.md`, `DESIGN.md`, and `IMPLEMENTATION.md`; plus the current phase's `PHASE_XX_..._DESIGN.md` and `PHASE_XX_..._IMPLEMENTATION.md`).
- Update documentation in the same work session when reality changes:
  - `DESIGN.md` if architecture/bounded-context boundaries or cross-cutting rules change.
  - `IMPLEMENTATION.md` if a new global convention is introduced or a documented convention changes.
  - The current phase implementation notes (`PHASE_XX_..._IMPLEMENTATION.md`) if the step plan, dependencies, testing approach, or implementation details change.
  - Append to `ACTIVITY.md` for notable milestones (major features, architectural decisions, or significant bugs fixed).
- Do not let docs drift: if the code contradicts the docs, stop and reconcile before proceeding.

## Implementation

When we reach the implementation phase:

- **One logical change per commit.** Each commit should be one self-contained, understandable step. If adding an entity naturally requires the entity class and its repository to compile, that is one logical change. The build must pass after every commit.
- **Use standard tools and generators where they exist.** For example, use Spring Initializr to generate the project skeleton rather than writing build files by hand. Commit the generated output as-is first, then modify on top, so the diff shows what is "default" vs. what is "our choice".

- **MANDATORY READING:** You must read `DESIGN_RULES.md` alongside `ATPEZMS.md` to understand the architectural constraints and the Iterative Waterfall process before beginning any work.
