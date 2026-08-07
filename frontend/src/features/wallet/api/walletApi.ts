import { httpClient } from '@/shared/api/httpClient'

import type { WalletHomeResponse } from '../model/walletHome'

export const getWalletHome = async (): Promise<WalletHomeResponse> => {
  const { data } = await httpClient.get<WalletHomeResponse>('/api/v1/wallet')

  return data
}
