// Supabase sync client for DeskPet
const SUPABASE_URL = 'https://rvnruqwusqaynrcphgod.supabase.co';
const SUPABASE_KEY = 'sb_publishable_1o7IA1_fUweDJ2mRKj_YNw_ClbpBSzq';

class SupabaseSync {
    constructor(petEngine) {
        this.petEngine = petEngine;
        this.lastState = '';
        this.pollInterval = null;
    }

    start() {
        this.poll();
        this.pollInterval = setInterval(() => this.poll(), 5000);
    }

    stop() {
        if (this.pollInterval) {
            clearInterval(this.pollInterval);
        }
    }

    async poll() {
        try {
            const resp = await fetch(`${SUPABASE_URL}/rest/v1/clawd_state?select=*&order=created_at.desc&limit=1`, {
                headers: {
                    'apikey': SUPABASE_KEY,
                    'Authorization': `Bearer ${SUPABASE_KEY}`
                }
            });
            const data = await resp.json();
            if (data && data.length > 0) {
                const state = data[0];
                if (state.state && state.state !== this.lastState) {
                    this.lastState = state.state;
                    this.petEngine.setState(state.state);
                }
                if (state.bubble_text) {
                    this.petEngine.showBubble(state.bubble_text, state.bubble_style || 'normal');
                }
            }
        } catch (e) {
            // Silently retry
        }
    }

    async report(event) {
        try {
            await fetch(`${SUPABASE_URL}/rest/v1/clawd_events`, {
                method: 'POST',
                headers: {
                    'apikey': SUPABASE_KEY,
                    'Authorization': `Bearer ${SUPABASE_KEY}`,
                    'Content-Type': 'application/json',
                    'Prefer': 'return=minimal'
                },
                body: JSON.stringify(event)
            });
        } catch (e) {}
    }
}
