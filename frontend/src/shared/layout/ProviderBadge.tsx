import { useProvider } from '../../api/queries'
import './ProviderBadge.css'

/** How the header names each provider the profiles offer; any other name is shown as the API sends it. */
const PROVIDER_NAMES: Record<string, string> = { openai: 'OpenAI', ollama: 'Ollama' }

/**
 * The model provider the API runs on, in the header of every screen (Brief FR-21; Document 2, API Surface,
 * GET /system/provider): the provider, the model each role asks and the embedding model with its dimension, as the
 * active profile names them. While the route has not answered it shows nothing, and when it cannot be read it says so
 * rather than guess a provider.
 */
export function ProviderBadge() {
  const provider = useProvider()
  if (provider.isPending) {
    return null
  }
  return (
    <section className="provider" aria-label="Model provider">
      <dl className="provider__names">
        {provider.isError ? (
          <div className="provider__row">
            <dt>Provider</dt>
            <dd className="provider__name">Unknown</dd>
          </div>
        ) : (
          <>
            <div className="provider__row">
              <dt>Provider</dt>
              <dd className="provider__name">
                {PROVIDER_NAMES[provider.data.provider] ?? provider.data.provider}
              </dd>
            </div>
            <div className="provider__row" title="Authors rules, reviews them and proposes changes">
              <dt>Strong model</dt>
              <dd className="mono">{provider.data.chatModels.strong}</dd>
            </div>
            <div className="provider__row" title="Explains decisions and answers questions">
              <dt>Fast model</dt>
              <dd className="mono">{provider.data.chatModels.fast}</dd>
            </div>
            <div className="provider__row" title="Embeds the policy and the rules for retrieval">
              <dt>Embeddings</dt>
              <dd className="mono">
                <span className="provider__piece">{provider.data.embeddingModel}</span>
                {', '}
                <span className="provider__piece">{`${provider.data.embeddingDimension} dimensions`}</span>
              </dd>
            </div>
          </>
        )}
      </dl>
    </section>
  )
}
