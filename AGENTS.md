# Repository Guidelines

## Project Structure & Module Organization
This repository currently stores the project brief as Markdown prompts in [`promts/`](./promts). The main flow is:

- `promts/prompt.md`: master product and architecture brief
- `promts/step_1.md` to `promts/step_5.md`: staged implementation and review tasks

There is no application source tree yet. When adding the actual project, follow the structure described in the prompts: `backend/`, `client/`, `shared-contract/`, `infra/`, plus top-level documentation such as `README.md` and `docs/`.

## Build, Test, and Development Commands
No build system is checked in yet. For now, contributors mainly inspect and edit the prompt set:

- `rg --files`: list tracked files quickly
- `sed -n '1,80p' promts/step_1.md`: preview a prompt section
- `markdownlint AGENTS.md`: optional Markdown validation if available locally

When introducing code, document the canonical commands in `README.md` and keep them runnable from the repository root.

## Coding Style & Naming Conventions
Use clear, task-focused Markdown with short sections and numbered execution steps where helpful. Keep filenames lowercase with underscores for staged prompts, matching the existing pattern such as `step_3.md`.

If you add source modules, keep directory names explicit and stable (`shared-contract`, not abbreviations), and prefer language-standard formatters and linters. Do not commit IDE-specific changes from `.idea/` unless they are intentionally shared.

## Testing Guidelines
There is no automated test suite yet. For prompt or documentation changes, review for:

- internal consistency across `prompt.md` and `step_*.md`
- stable endpoint names, module names, and platform targets
- absence of contradictory requirements between steps

If you add executable code, add matching tests in the same change and document how to run them from the root.

## Commit & Pull Request Guidelines
The Git history is currently empty, so no repository-specific commit convention exists yet. Use concise, imperative commit messages such as `Add deployment prompt refinements` or `Create shared-contract skeleton brief`.

Pull requests should include a short summary, affected files, rationale for any prompt or architecture changes, and sample output or screenshots when rendering/documentation behavior changes materially.

## Configuration & Repository Hygiene
Respect `.dockerignore`: do not commit generated `build/`, `.gradle/`, local `client/` artifacts, or editor clutter. Keep repository-level guidance current as soon as real modules, tooling, or CI are added.
