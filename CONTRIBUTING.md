# Contributing

Thanks for collaborating on this project. To keep work organized and avoid merge conflicts, please follow these practices.

## Git Branching

**Always create a branch before adding new code.** This keeps your work separate from others and makes merging cleaner.

```bash
# Create and switch to a new branch (use your name + feature)
git checkout -b dalton/feature_name

# Examples:
git checkout -b dalton/points-double-farder
git checkout -b dalton/spawn-costs-update
```

**Workflow:**
1. Create a branch: `git checkout -b yourname/feature_description`
2. Make your changes and commit: `git add .` then `git commit -m "Description of change"`
3. Push your branch: `git push -u origin yourname/feature_description`
4. Open a pull request (or merge when ready): your branch → `main` (or `master`)

**Why?** If multiple people edit the same files, you'll get merge conflicts. Branches keep work isolated until you're ready to combine it.

## Testing the Streaming Setup

To test or verify changes to the overlay, points system, or chat spawn features, you need to emulate the full setup. See **[docs/streaming-setup-guide.md](docs/streaming-setup-guide.md)** for step-by-step instructions.

## Key Paths to Customize

If you're running this on a different machine, update these paths:

- **config.json:** `save_directory` (SPD save file location; copy from `config.example.json`)
- **docs/streamerbot-points-from-scratch.md:** All `FILE`, `DOUBLE_FILE`, `TOP_FARDER_FILE` paths in the C# code
- **points_command.py:** Uses `SCRIPT_DIR` for points file; fetches depth from `http://127.0.0.1:5000/api/game-data`
