#!/bin/bash

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}OnPush Change Detection Verification${NC}"
echo -e "${BLUE}========================================${NC}\n"

# Find all components with OnPush
echo -e "${YELLOW}📋 Scanning for OnPush components...${NC}\n"

ONPUSH_FILES=$(grep -r "ChangeDetectionStrategy.OnPush" src/app --include="*.ts" -l)
ONPUSH_COUNT=$(echo "$ONPUSH_FILES" | wc -l | tr -d ' ')

echo -e "${GREEN}✓ Found $ONPUSH_COUNT components with OnPush:${NC}\n"

while IFS= read -r file; do
    COMPONENT_NAME=$(basename "$file" .ts)
    echo -e "  • ${GREEN}$COMPONENT_NAME${NC}"
    echo -e "    ${BLUE}$file${NC}"
done <<< "$ONPUSH_FILES"

echo ""

# Check for proper @Input() usage
echo -e "${YELLOW}🔍 Verifying @Input() decorators...${NC}\n"

INPUT_ISSUES=0
while IFS= read -r file; do
    # Check if file has @Input() decorators
    if grep -q "@Input()" "$file"; then
        INPUTS=$(grep -c "@Input()" "$file")
        echo -e "  ${GREEN}✓${NC} $(basename "$file"): $INPUTS input(s)"
    else
        echo -e "  ${YELLOW}⚠${NC} $(basename "$file"): No inputs (may be OK for pure presentational)"
    fi
done <<< "$ONPUSH_FILES"

echo ""

# Check for potential issues (mutating inputs)
echo -e "${YELLOW}⚠️  Checking for potential OnPush issues...${NC}\n"

echo -e "${BLUE}Checking for array mutations (push, pop, shift, unshift):${NC}"
MUTATION_FILES=$(grep -r "\.push\|\.pop\|\.shift\|\.unshift" src/app/features src/app/shared --include="*.ts" | grep -v "spec.ts" | grep -v "node_modules")
if [ -z "$MUTATION_FILES" ]; then
    echo -e "  ${GREEN}✓${NC} No array mutations found"
else
    echo -e "  ${YELLOW}⚠${NC} Found potential array mutations (verify these create new references):"
    echo "$MUTATION_FILES" | while IFS= read -r line; do
        echo -e "    ${YELLOW}→${NC} $line"
    done
fi

echo ""

echo -e "${BLUE}Checking for object property mutations:${NC}"
# This is a simple heuristic - may have false positives
OBJECT_MUTATIONS=$(grep -r "this\.\w\+\.\w\+ =" src/app/features src/app/shared --include="*.ts" | grep -v "spec.ts" | grep -v "node_modules" | grep -v "= {" | head -10)
if [ -z "$OBJECT_MUTATIONS" ]; then
    echo -e "  ${GREEN}✓${NC} No obvious object mutations found"
else
    echo -e "  ${YELLOW}⚠${NC} Found potential object property mutations (review these):"
    echo "$OBJECT_MUTATIONS" | while IFS= read -r line; do
        echo -e "    ${YELLOW}→${NC} $line"
    done
fi

echo ""

# Check for async pipe usage
echo -e "${YELLOW}📡 Checking async pipe usage...${NC}\n"

ASYNC_USAGE=$(grep -r "| async" src/app --include="*.html" | wc -l | tr -d ' ')
echo -e "  ${GREEN}✓${NC} Found $ASYNC_USAGE uses of async pipe (good for OnPush)"

echo ""

# Calculate OnPush adoption percentage
TOTAL_COMPONENTS=$(find src/app -name "*.component.ts" | grep -v "spec.ts" | wc -l | tr -d ' ')
ONPUSH_PERCENT=$((ONPUSH_COUNT * 100 / TOTAL_COMPONENTS))

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}Summary${NC}"
echo -e "${BLUE}========================================${NC}\n"

echo -e "Total Components: ${YELLOW}$TOTAL_COMPONENTS${NC}"
echo -e "OnPush Components: ${GREEN}$ONPUSH_COUNT${NC}"
echo -e "OnPush Adoption: ${GREEN}$ONPUSH_PERCENT%${NC}"
echo -e "Target: ${YELLOW}80%${NC} (for presentational components)\n"

if [ "$ONPUSH_PERCENT" -ge 80 ]; then
    echo -e "${GREEN}✓ OnPush target achieved!${NC}\n"
elif [ "$ONPUSH_PERCENT" -ge 50 ]; then
    echo -e "${YELLOW}⚠ Good progress, but more components can benefit from OnPush${NC}\n"
else
    echo -e "${RED}✗ OnPush adoption is low. Consider adding to more presentational components${NC}\n"
fi

# Run OnPush-specific tests if they exist
echo -e "${YELLOW}🧪 Checking for OnPush-specific tests...${NC}\n"

ONPUSH_TESTS=$(find src/app -name "*.onpush.spec.ts" | wc -l | tr -d ' ')
if [ "$ONPUSH_TESTS" -gt 0 ]; then
    echo -e "  ${GREEN}✓${NC} Found $ONPUSH_TESTS OnPush test file(s)"
    echo -e "\n${BLUE}Run OnPush tests with:${NC}"
    echo -e "  ${GREEN}npm test -- --include='**/*.onpush.spec.ts'${NC}\n"
else
    echo -e "  ${YELLOW}⚠${NC} No OnPush-specific tests found"
    echo -e "  ${BLUE}Consider creating tests to verify OnPush behavior${NC}\n"
fi

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}Next Steps${NC}"
echo -e "${BLUE}========================================${NC}\n"

echo -e "1. ${GREEN}Run the test:${NC}"
echo -e "   ${BLUE}npm test -- --include='**/status-badge.component.onpush.spec.ts'${NC}\n"

echo -e "2. ${GREEN}Manual browser test:${NC}"
echo -e "   ${BLUE}npm start${NC}"
echo -e "   Open browser console and run:"
echo -e "   ${BLUE}ng.profiler.timeChangeDetection()${NC}\n"

echo -e "3. ${GREEN}Review the comprehensive guide:${NC}"
echo -e "   ${BLUE}cat docs/TESTING_ONPUSH.md${NC}\n"

echo -e "${GREEN}✓ Verification complete!${NC}\n"
