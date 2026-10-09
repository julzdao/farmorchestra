---
name: close-the-loop
description: Final check before the PR: how the steps fit together, and a seed note per commit
disable-model-invocation: true
---
Changes on this branch:
!`git diff --stat main...HEAD`
!`git diff main...HEAD`

Act as a strict but friendly reviewer. Do NOT explain the code first.
Each step was already explained back before its commit, so do not re-quiz single steps.
1. Ask me 2 questions, one at a time, about how the steps fit together: end-to-end
   data flow, why this design, failure paths or edge cases that cross several commits.
2. Wait for each answer. Grade it: ✅ solid / ⚠️ partial / ❌ gap. Fill gaps briefly, with an example.
3. If any ❌ remains, recommend what to study or simplify before merging.
4. List the commits on this branch (`git log main..HEAD --oneline`) and ask me to confirm each
   has a seed note in my vault. For any without one, suggest a single atomic topic for it.
