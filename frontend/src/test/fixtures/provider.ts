import type { ProviderResponse } from '../../api/types'

/**
 * What GET /system/provider answers under each profile (Document 2, API Surface, and the profiles table of
 * Configuration and Model Providers): the names each profile file sets, never a key or an address.
 */

export const openAiProvider: ProviderResponse = {
  provider: 'openai',
  chatModels: { strong: 'gpt-5.6-terra', fast: 'gpt-5.6-luna' },
  embeddingModel: 'text-embedding-3-small',
  embeddingDimension: 1536,
}

export const ollamaProvider: ProviderResponse = {
  provider: 'ollama',
  chatModels: { strong: 'qwen3:14b', fast: 'qwen3:14b' },
  embeddingModel: 'bge-m3',
  embeddingDimension: 1024,
}
