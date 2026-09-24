# Scroll Expansion Hero

## Purpose

RECARRY Hero에서 사용할 수 있는
scroll-driven immersive media component reference.

## Important Adaptation

이 문서는 원본 컴포넌트가 제공하는 구현 명세를 보존한 참고 문서다.

원본 코드는 Next.js 환경을 전제로 한다.

RECARRY 프로젝트는 React + Vite 기반이므로
실제 구현 시 다음과 같이 변환한다.

- next/image → native img 또는 프로젝트의 image component
- Next.js specific APIs → React/Vite equivalent
- @/ alias → 현재 Vite tsconfig 설정에 맞게 사용
- Next.js routing → 현재 routing 구조 사용

Next.js 자체를 프로젝트에 추가하기 위한 목적으로 사용하지 않는다.

## Dependency Reference

원본 기준:

- React
- framer-motion
- Tailwind CSS
- TypeScript
- shadcn/ui

RECARRY에 필요한 dependency만 선택적으로 적용한다.