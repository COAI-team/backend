#!/usr/bin/env node
import { Server } from "@modelcontextprotocol/sdk/server/index.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import {
  CallToolRequestSchema,
  ListToolsRequestSchema,
} from "@modelcontextprotocol/sdk/types.js";
import axios from 'axios';

// 환경 변수 설정 (기본값: 로컬 개발 서버)
const SERVER_URL = process.env.COAI_SERVER_URL || 'https://api.co-ai.run/api/mcp/analyze';
const MCP_TOKEN = process.env.COAI_MCP_TOKEN || 'codenose-mcp-secret-key';
const USER_ID = process.env.COAI_USER_ID || '1';

// HTTPS 인증서 무시 (개발용/Self-signed 이슈 방지)
// HTTPS 인증서 무시 (로컬 개발용: localhost일 때만)
const isLocal = SERVER_URL.includes('localhost') || SERVER_URL.includes('127.0.0.1');
const axiosInstance = axios.create({
  httpsAgent: new (await import('https')).Agent({  
    rejectUnauthorized: !isLocal 
  })
});

const server = new Server(
  {
    name: "codenose-analysis-server",
    version: "0.1.0",
  },
  {
    capabilities: {
      tools: {},
    },
  }
);

// 1. Tool 목록 정의
server.setRequestHandler(ListToolsRequestSchema, async () => {
  return {
    tools: [
      {
        name: "analyze_code_with_coai",
        description: "Analyze code using the Coai (CodeNose) AI engine to find bugs, mistakes, and improvements.",
        inputSchema: {
          type: "object",
          properties: {
            code: {
              type: "string",
              description: "The source code content to analyze",
            },
            language: {
              type: "string",
              description: "The programming language of the code (e.g., java, python, js)",
              default: "java"
            }
          },
          required: ["code"],
        },
      },
    ],
  };
});

// 2. Tool 실행 핸들러
server.setRequestHandler(CallToolRequestSchema, async (request) => {
  if (request.params.name === "analyze_code_with_coai") {
    const { code, language } = request.params.arguments;

    try {
      // Spring Boot 서버로 요청 전송
      const response = await axiosInstance.post(SERVER_URL, {
        code: code,
        language: language || 'java',
        userId: USER_ID
      }, {
        headers: {
            'X-MCP-Token': MCP_TOKEN,
            'Content-Type': 'application/json'
        }
      });

      const analysisResult = response.data.result;

      return {
        content: [
          {
            type: "text",
            text: typeof analysisResult === 'string' ? analysisResult : JSON.stringify(analysisResult, null, 2),
          },
        ],
      };
    } catch (error) {
      const errorMsg = error.response?.data?.error || error.message;
      return {
        content: [
          {
            type: "text",
            text: `Analysis Failed: ${errorMsg}`,
          },
        ],
        isError: true,
      };
    }
  }

  throw new Error("Tool not found");
});

// 서버 시작
const transport = new StdioServerTransport();
await server.connect(transport);
