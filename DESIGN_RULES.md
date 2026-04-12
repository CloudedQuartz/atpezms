# ATPEZMS Design & Engineering Rules

## Core Methodology: Iterative Waterfall
We use an **Iterative Waterfall** approach. This balances upfront architectural planning with the practicality of feature-by-feature implementation.

### Level 1: Global Blueprint (The Long-Term View)
Before any feature code is written, a global `design.md` MUST be created. It must define:
1. **System Architecture:** Standard N-Tier layers (Controller -> Service -> Repository). 
2. **Global Infrastructure:** Define the standard API response structures, Global Exception Handling strategy, and Security flow.
3. **Domain Map:** Divide the system into high-level Bounded Contexts (e.g., Identity, Ticketing, Operations) based on `ATPEZMS.md`.
4. **Tech Stack & Standards:** Finalize database, framework modules, and naming conventions.

### Level 2: The Feature "Mini-Waterfall" (Vertical Slices)
Features are built one Bounded Context at a time. Each slice goes through its own strict waterfall:
1. **Detailed Design:** Map out the exact database schema, entities, and REST API contracts for *this specific slice*.
2. **Implementation:** Write the code strictly adhering to the Level 1 Blueprint.
3. **Verification:** Test the slice thoroughly.
4. **Education:** Explain the concepts and annotations introduced in this slice.

## Blueprint Evolution & Cross-Referencing
The global `design.md` is a living blueprint, not a static stone tablet. 
- **Continual Cross-Referencing:** During the Detailed Design and Implementation of every vertical slice, continually cross-reference the work against `design.md` to ensure alignment with the global architecture.
- **Iterative Updates:** If building a specific feature reveals a flaw in the global design, or requires a new global pattern (e.g., a new infrastructure component), **stop and update `design.md` first**. The blueprint must always reflect reality, and reality must reflect the blueprint.