#!/bin/bash
# Test Script: Database Schema Verification
# Verifies database schema structure and dual-hash setup
#
# Usage: ./test-database-schema.sh [db-host] [db-port] [db-name]
#
# Defaults:
#   db-host: localhost
#   db-port: 5432
#   db-name: robin

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
DB_HOST="${1:-localhost}"
DB_PORT="${2:-5432}"
DB_NAME="${3:-robin}"
DB_USER="${PGUSER:-postgres}"
DB_PASS="${PGPASSWORD:-postgres}"

# Test counters
TESTS_TOTAL=0
TESTS_PASSED=0
TESTS_FAILED=0

echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}Database Schema Verification Test Suite${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""
echo "Database: ${DB_HOST}:${DB_PORT}/${DB_NAME}"
echo ""

# Helper function to run SQL query
run_sql() {
    PGPASSWORD="${DB_PASS}" psql -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d "${DB_NAME}" -t -A -c "$1" 2>&1
}

# Helper function to run a test
run_test() {
    local test_name="$1"
    shift

    TESTS_TOTAL=$((TESTS_TOTAL + 1))

    echo -n "Testing: ${test_name}... "

    if "$@" > /dev/null 2>&1; then
        echo -e "${GREEN}PASSED${NC}"
        TESTS_PASSED=$((TESTS_PASSED + 1))
        return 0
    else
        echo -e "${RED}FAILED${NC}"
        TESTS_FAILED=$((TESTS_FAILED + 1))
        return 1
    fi
}

# Check dependencies
echo -e "${YELLOW}Checking dependencies...${NC}"
if ! command -v psql &> /dev/null; then
    echo -e "${RED}ERROR: psql is not installed${NC}"
    exit 1
fi
echo -e "${GREEN}Dependencies OK${NC}"
echo ""

# Test database connection
echo -e "${YELLOW}Testing database connection...${NC}"
if ! run_sql "SELECT 1;" > /dev/null 2>&1; then
    echo -e "${RED}ERROR: Cannot connect to database${NC}"
    echo "Host: ${DB_HOST}:${DB_PORT}"
    echo "Database: ${DB_NAME}"
    echo "User: ${DB_USER}"
    exit 1
fi
echo -e "${GREEN}Database connection OK${NC}"
echo ""

# ============================================
# TEST SUITE 1: Required Tables
# ============================================
echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}TEST SUITE 1: Required Tables${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""

test_table_exists() {
    local table_name="$1"
    local result=$(run_sql "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = '${table_name}';")
    [ "$result" = "1" ]
}

run_test "users table exists" test_table_exists "users"
run_test "flyway_schema_history table exists" test_table_exists "flyway_schema_history"

echo ""

# ============================================
# TEST SUITE 2: Users Table Schema
# ============================================
echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}TEST SUITE 2: Users Table Schema${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""

test_column_exists() {
    local column_name="$1"
    local result=$(run_sql "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'users' AND column_name = '${column_name}';")
    [ "$result" = "1" ]
}

run_test "id column exists" test_column_exists "id"
run_test "username column exists" test_column_exists "username"
run_test "email column exists" test_column_exists "email"
run_test "password column exists" test_column_exists "password"
run_test "password_bcrypt column exists" test_column_exists "password_bcrypt"
run_test "enabled column exists" test_column_exists "enabled"
run_test "created_at column exists" test_column_exists "created_at"
run_test "updated_at column exists" test_column_exists "updated_at"

echo ""

# ============================================
# TEST SUITE 3: Password Columns Configuration
# ============================================
echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}TEST SUITE 3: Password Columns Configuration${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""

test_column_type() {
    local column_name="$1"
    local expected_type="$2"
    local result=$(run_sql "SELECT data_type FROM information_schema.columns WHERE table_name = 'users' AND column_name = '${column_name}';")
    [ "$result" = "$expected_type" ]
}

run_test "password column is VARCHAR" test_column_type "password" "character varying"
run_test "password_bcrypt column is VARCHAR" test_column_type "password_bcrypt" "character varying"

test_column_length() {
    local column_name="$1"
    local min_length="$2"
    local result=$(run_sql "SELECT character_maximum_length FROM information_schema.columns WHERE table_name = 'users' AND column_name = '${column_name}';")
    [ "$result" -ge "$min_length" ]
}

run_test "password column length >= 255" test_column_length "password" "255"
run_test "password_bcrypt column length >= 255" test_column_length "password_bcrypt" "255"

echo ""

# ============================================
# TEST SUITE 4: Indexes and Constraints
# ============================================
echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}TEST SUITE 4: Indexes and Constraints${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""

test_index_exists() {
    local index_pattern="$1"
    local result=$(run_sql "SELECT COUNT(*) FROM pg_indexes WHERE tablename = 'users' AND indexname ~ '${index_pattern}';")
    [ "$result" -ge "1" ]
}

run_test "Primary key on id column" test_index_exists "pkey"
run_test "Unique index on username" test_index_exists "username"
run_test "Index on password_bcrypt" test_index_exists "password_bcrypt" || echo -e "${YELLOW}  (Optional index - not critical)${NC}"

echo ""

# ============================================
# TEST SUITE 5: Flyway Migrations
# ============================================
echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}TEST SUITE 5: Flyway Migrations${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""

test_migration_applied() {
    local version="$1"
    local result=$(run_sql "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '${version}' AND success = true;")
    [ "$result" = "1" ]
}

run_test "V1 migration applied" test_migration_applied "1" || echo -e "${YELLOW}  (Initial schema - may be named differently)${NC}"
run_test "V2 migration applied" test_migration_applied "2"
run_test "V3 migration applied" test_migration_applied "3"

echo ""

# ============================================
# TEST SUITE 6: Admin User Configuration
# ============================================
echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}TEST SUITE 6: Admin User Configuration${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""

test_admin_user_exists() {
    local result=$(run_sql "SELECT COUNT(*) FROM users WHERE username = 'admin@robin.local';")
    [ "$result" = "1" ]
}

run_test "Admin user exists" test_admin_user_exists

test_admin_has_password() {
    local result=$(run_sql "SELECT COUNT(*) FROM users WHERE username = 'admin@robin.local' AND password IS NOT NULL;")
    [ "$result" = "1" ]
}

run_test "Admin has password (SHA512-CRYPT)" test_admin_has_password

test_admin_has_bcrypt() {
    local result=$(run_sql "SELECT COUNT(*) FROM users WHERE username = 'admin@robin.local' AND password_bcrypt IS NOT NULL;")
    [ "$result" = "1" ]
}

run_test "Admin has password_bcrypt (BCrypt)" test_admin_has_bcrypt

test_admin_enabled() {
    local result=$(run_sql "SELECT enabled FROM users WHERE username = 'admin@robin.local';")
    [ "$result" = "t" ]
}

run_test "Admin user is enabled" test_admin_enabled

echo ""

# ============================================
# TEST SUITE 7: Password Hash Format Validation
# ============================================
echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}TEST SUITE 7: Password Hash Format Validation${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""

test_bcrypt_format() {
    local hash=$(run_sql "SELECT password_bcrypt FROM users WHERE username = 'admin@robin.local';")
    [[ "$hash" =~ ^\$2[ayb]\$.{56}$ ]]
}

run_test "BCrypt hash has valid format" test_bcrypt_format

test_sha512_format() {
    local hash=$(run_sql "SELECT password FROM users WHERE username = 'admin@robin.local';")
    [[ "$hash" =~ ^\{SHA512-CRYPT\}\$6\$ ]]
}

run_test "SHA512-CRYPT hash has valid format" test_sha512_format

echo ""

# ============================================
# TEST SUITE 8: Data Integrity
# ============================================
echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}TEST SUITE 8: Data Integrity${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""

test_no_duplicate_usernames() {
    local result=$(run_sql "SELECT COUNT(*) FROM (SELECT username, COUNT(*) as cnt FROM users GROUP BY username HAVING COUNT(*) > 1) as duplicates;")
    [ "$result" = "0" ]
}

run_test "No duplicate usernames" test_no_duplicate_usernames

test_all_users_have_bcrypt() {
    local total=$(run_sql "SELECT COUNT(*) FROM users;")
    local with_bcrypt=$(run_sql "SELECT COUNT(*) FROM users WHERE password_bcrypt IS NOT NULL;")
    [ "$total" = "$with_bcrypt" ]
}

run_test "All users have BCrypt hash" test_all_users_have_bcrypt || echo -e "${YELLOW}  (Some users may not have BCrypt - needs migration)${NC}"

test_all_users_have_sha512() {
    local total=$(run_sql "SELECT COUNT(*) FROM users;")
    local with_sha512=$(run_sql "SELECT COUNT(*) FROM users WHERE password IS NOT NULL;")
    [ "$total" = "$with_sha512" ]
}

run_test "All users have SHA512-CRYPT hash" test_all_users_have_sha512

echo ""

# ============================================
# Display Schema Information
# ============================================
echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}Schema Information${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""

echo -e "${YELLOW}Users table columns:${NC}"
run_sql "SELECT column_name, data_type, character_maximum_length, is_nullable FROM information_schema.columns WHERE table_name = 'users' ORDER BY ordinal_position;" | column -t -s '|'
echo ""

echo -e "${YELLOW}Indexes on users table:${NC}"
run_sql "SELECT indexname, indexdef FROM pg_indexes WHERE tablename = 'users' ORDER BY indexname;"
echo ""

echo -e "${YELLOW}Applied Flyway migrations:${NC}"
run_sql "SELECT version, description, installed_on, success FROM flyway_schema_history ORDER BY installed_rank;" | column -t -s '|'
echo ""

echo -e "${YELLOW}User statistics:${NC}"
echo -n "Total users: "
run_sql "SELECT COUNT(*) FROM users;"
echo -n "Users with BCrypt: "
run_sql "SELECT COUNT(*) FROM users WHERE password_bcrypt IS NOT NULL;"
echo -n "Users with SHA512-CRYPT: "
run_sql "SELECT COUNT(*) FROM users WHERE password IS NOT NULL;"
echo -n "Enabled users: "
run_sql "SELECT COUNT(*) FROM users WHERE enabled = true;"
echo ""

# ============================================
# TEST SUMMARY
# ============================================
echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}TEST SUMMARY${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""
echo "Total Tests: ${TESTS_TOTAL}"
echo -e "Passed: ${GREEN}${TESTS_PASSED}${NC}"
echo -e "Failed: ${RED}${TESTS_FAILED}${NC}"
echo ""

if [ ${TESTS_FAILED} -eq 0 ]; then
    echo -e "${GREEN}✓ Database schema is correctly configured!${NC}"
    echo ""
    echo "Dual-hash password strategy is active:"
    echo "  ✓ password column (SHA512-CRYPT) for MTA/Dovecot"
    echo "  ✓ password_bcrypt column (BCrypt) for Gateway"
    exit 0
else
    echo -e "${RED}✗ Some schema tests failed${NC}"
    echo ""
    echo -e "${YELLOW}Common issues:${NC}"
    echo "  - V3 migration not applied: Run robin-gateway to apply migrations"
    echo "  - Missing password_bcrypt column: Apply V3__add_bcrypt_password.sql"
    echo "  - Invalid hash formats: Reset admin password via Gateway API"
    exit 1
fi
