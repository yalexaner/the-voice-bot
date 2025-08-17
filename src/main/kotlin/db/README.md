# Database Package

This package handles SQLite database operations for user management and invite tokens.

## Planned Components

- `Database.kt` - Database initialization and migration system
- `UserRepository.kt` - User CRUD operations
- `InviteRepository.kt` - Invite token management
- `DatabaseMigrations.kt` - Schema versioning and migrations

## Database Schema

### users table
- `telegram_user_id` (PK) - User's Telegram ID
- `username` - Telegram username
- `status` - active/blocked
- `added_by` - Admin who authorized the user
- `added_at` - Authorization timestamp

### invites table
- `token` (PK) - Invite token
- `created_by` - Admin who created the invite
- `created_at` - Creation timestamp
- `expires_at` - Expiration (nullable)
- `used_by` - User who used the invite (nullable)
- `used_at` - Usage timestamp (nullable)

## Status

🚧 **Under Development** - This package will be implemented in Phase 3 of the project.

See `docs/spec/todo.md` section 8 for implementation details.