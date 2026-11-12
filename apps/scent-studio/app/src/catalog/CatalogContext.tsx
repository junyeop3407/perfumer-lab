/**
 * 앱 전체에서 쓰는 카탈로그·추천 엔진. 앱이 켜질 때 한 번 열고(startCatalog), L9 시간을 로그로 남긴다.
 * 상태: loading → ready | error(다시 시도).
 */
import AsyncStorage from '@react-native-async-storage/async-storage';
import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react';

import { bootCatalog, l9Line, type Booted } from './catalogBoot';
import { startCatalog } from './startCatalog';

export type CatalogState =
  | { status: 'loading' }
  | ({ status: 'ready' } & Booted)
  | { status: 'error'; retry: () => void };

const Ctx = createContext<CatalogState>({ status: 'loading' });

const INSTALL_ID_KEY = 'scent.installId';

/** 설치마다 하나인 임의 id (카탈로그 새 버전 받기 횟수 제한용, 개인정보 아님) */
async function installId(): Promise<string> {
  try {
    const saved = await AsyncStorage.getItem(INSTALL_ID_KEY);
    if (saved) return saved;
  } catch {
    // 저장소를 못 읽어도 앱은 연다
  }
  const id = Array.from({ length: 32 }, () => Math.floor(Math.random() * 16).toString(16)).join('');
  try {
    await AsyncStorage.setItem(INSTALL_ID_KEY, id);
  } catch {
    // 다음 실행에 새로 만든다
  }
  return id;
}

export function CatalogProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<CatalogState>({ status: 'loading' });
  const [attempt, setAttempt] = useState(0);
  const retry = useCallback(() => {
    setState({ status: 'loading' });
    setAttempt((a) => a + 1);
  }, []);

  useEffect(() => {
    let alive = true;
    const now = () => (typeof performance !== 'undefined' ? performance.now() : Date.now());
    (async () => {
      try {
        const id = await installId();
        const booted = await bootCatalog(() => startCatalog({ installId: id }), now);
        console.log(l9Line(booted.timing, booted.origin));
        if (alive) setState({ status: 'ready', ...booted });
      } catch (e) {
        console.warn(`[catalog] 열지 못함: ${(e as Error).message}`);
        if (alive) setState({ status: 'error', retry });
      }
    })();
    return () => {
      alive = false;
    };
  }, [attempt, retry]);

  return <Ctx.Provider value={state}>{children}</Ctx.Provider>;
}

export function useCatalog(): CatalogState {
  return useContext(Ctx);
}
