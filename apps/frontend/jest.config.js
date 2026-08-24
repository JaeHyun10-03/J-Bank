const nextJest = require("next/jest");

const createJestConfig = nextJest({ dir: "./" });

/** @type {import('jest').Config} */
const customJestConfig = {
  testEnvironment: "jest-environment-jsdom",
  // <rootDir>로 절대경로를 넣으면 이 저장소 경로에 포함된 "[02]"가 정규식 문자클래스로
  // 해석돼 매치가 깨진다(testPathIgnorePatterns는 정규식). 부분 문자열로만 지정한다.
  testPathIgnorePatterns: ["/node_modules/", "/e2e/"],
};

module.exports = createJestConfig(customJestConfig);
