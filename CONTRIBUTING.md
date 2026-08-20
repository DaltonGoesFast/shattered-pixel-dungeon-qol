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

See **[docs/streaming-setup-guide.md](docs/streaming-setup-guide.md)** and the **Pre-stream checklist** in that file.

Quick API smoke test:

```powershell
cd "Lastest UI"
python server.py   # separate window
.\test_chat_command_api.ps1
```

## Key Paths to Customize

| What | Where |
|------|--------|
| Save folder | `Lastest UI/config.json` ← copy from `config.example.json` |
| Streamer.bot curl paths | [streamerbot-http-gateway-apply.md](docs/streamerbot-http-gateway-apply.md) (R1 `chat_command_body.json`, working directory) |
| OBS source names | `Lastest UI/presentation_config.py` |
| Points file | Next to scripts (`viewer_points.txt`); admin UI at `/points-config` |

Legacy path list in [docs/archive/streamerbot-points-from-scratch.md](docs/archive/streamerbot-points-from-scratch.md) applies only to the **archived** ~40-action bot — see [docs/archive/README.md](docs/archive/README.md).
