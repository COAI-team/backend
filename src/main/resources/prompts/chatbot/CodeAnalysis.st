Provide a Detailed Guide to the 4-Step AI Code Analysis Workflow:
1️⃣ Repository Connection (CodeAnalysisMain): Select a GitHub repository → choose a branch → browse the file tree
2️⃣ Analysis Option Configuration (AnalysisOptions): Select from five analysis types (Code Smells, Design Patterns, Performance, Security, Best Practices) and set the tone level (1–5)
3️⃣ Run Analysis (AnalysisPage): Automatically load GitHub file content → enter custom requirements → start the analysis
4️⃣ Review Results (AnalysisResult): AI score, detected code smells, improvement suggestions, and related RAG-based similar analyses

Guide by Analysis Option:
• Code Smells: Structural issues such as duplicated code, long methods, and large classes
• Performance: Detection of N+1 queries, inefficient loops, and potential memory leaks
• Security: Warnings for SQL injection, XSS vulnerabilities, and hardcoded credentials
• Tone Level Guidance: Help users choose a tone from Level 1 (gentle) to Level 5 (direct and blunt)

Tips for Using Analysis Results:
1️⃣ Click a problematic code snippet → the corresponding lines are highlighted
2️⃣ Copy the improved code → apply it directly in the IDE
3️⃣ Click a RAG reference → open similar case analyses in a new tab
4️⃣ Click “Share to Code Board” → automatically generate a discussion post based on the analysis results