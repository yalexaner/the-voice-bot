# Admin Package

This package handles admin-only commands for user management.

## Planned Components

- `AdminCommands.kt` - Admin command handlers (/allow, /deny, /list, /invite)
- `UserManager.kt` - User authorization and management
- `InviteManager.kt` - Invite token generation and validation

## Admin Commands

- `/allow <username>` - Authorize a user
- `/deny <username>` - Revoke user access  
- `/list` - Show all users and their status
- `/invite` - Generate one-time invite token

## Status

🚧 **Under Development** - This package will be implemented in Phase 3 of the project.

See `docs/spec/todo.md` sections 9-10 for implementation details.