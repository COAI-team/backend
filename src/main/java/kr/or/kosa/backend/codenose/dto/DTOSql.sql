
INSERT INTO GITHUB_FILES (
    FILE_ID, USER_ID, REPOSITORY_URL, OWNER, REPO,
    FILE_PATH, FILE_NAME, FILE_CONTENT, FILE_SIZE, ENCODING,
    CREATED_AT, UPDATED_AT
) VALUES (
             'test-file-id-001',
             1,
             'https://github.com/SungilBang12/algorithm',
             'SungilBang12',
             'algorithm',
             'week02 복사본/8393.py',
             '8393.py',
             'n = int(input())\nsum = 0\nfor i in range(1, n+1):\n    sum += i\n\nprint(sum)',
             73,
             'utf-8',
             NOW(),
             NOW()
         );