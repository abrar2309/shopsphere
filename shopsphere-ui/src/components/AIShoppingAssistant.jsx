import { useState } from 'react';
import './AIShoppingAssistant.css';

const suggestions = [
    'Comfortable cotton shirts',
    'Running shoes under 2000',
    'Everyday backpacks',
    'Wireless headphones',
];

export default function AIShoppingAssistant({ onSearch }) {
    const [query, setQuery] = useState('');
    const [open, setOpen] = useState(false);

    function submitSearch(event) {
        event.preventDefault();
        const cleaned = query.trim();
        if (!cleaned) return;
        setOpen(false);
        onSearch?.(cleaned);
    }

    return (
        <>
            <button
                type="button"
                className="shopsphere-ai-launcher"
                onClick={() => setOpen((current) => !current)}
                aria-label="Open ShopSphere AI shopping assistant"
                aria-expanded={open}
            >
                <span aria-hidden="true">✦</span>
                <span>AI Assistant</span>
            </button>

            {open && (
                <div className="shopsphere-ai-panel" role="dialog" aria-modal="false" aria-label="ShopSphere AI Assistant">
                    <div className="shopsphere-ai-panel-header">
                        <div>
                            <span className="shopsphere-ai-kicker">SHOPSPHERE AI</span>
                            <h2>Your shopping assistant</h2>
                            <p>Describe what you need. I'll find matching products.</p>
                        </div>
                        <button type="button" onClick={() => setOpen(false)} aria-label="Close assistant">×</button>
                    </div>

                    <form className="shopsphere-ai-form" onSubmit={submitSearch}>
                        <input
                            type="search"
                            value={query}
                            onChange={(event) => setQuery(event.target.value)}
                            placeholder="e.g. running shoes under ₹2000"
                            aria-label="Describe what you want"
                            maxLength={500}
                        />
                        <button type="submit" disabled={!query.trim()}>Find</button>
                    </form>

                    <div className="shopsphere-ai-quick">
                        <span>Try asking</span>
                        {suggestions.map((suggestion) => (
                            <button
                                type="button"
                                key={suggestion}
                                onClick={() => {
                                    setQuery(suggestion);
                                    setOpen(false);
                                    onSearch?.(suggestion);
                                }}
                            >
                                {suggestion}
                            </button>
                        ))}
                    </div>
                    <div className="shopsphere-ai-powered">
                        Powered by ShopSphere BGE semantic product search
                    </div>
                </div>
            )}
        </>
    );
}