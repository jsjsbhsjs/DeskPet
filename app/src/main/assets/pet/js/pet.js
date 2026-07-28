// DeskPet Engine - Core pet logic
(function() {
    'use strict';

    const PET = {
        // State machine
        currentState: 'idle',
        prevState: 'idle',
        isSleeping: false,
        idleTimer: null,
        idleMinutes: 0,
        bubbleTimeout: null,

        // SVG mapping: state -> SVG file
        svgMap: {
            'idle': 'svg/clawd-idle-living.svg',
            'idle-look': 'svg/clawd-idle-look.svg',
            'idle-doze': 'svg/clawd-idle-doze.svg',
            'idle-yawn': 'svg/clawd-idle-yawn.svg',
            'idle-reading': 'svg/clawd-idle-reading.svg',
            'idle-bubble': 'svg/clawd-idle-bubble.svg',
            'happy': 'svg/clawd-happy.svg',
            'error': 'svg/clawd-error.svg',
            'sleeping': 'svg/clawd-sleeping.svg',
            'wake': 'svg/clawd-wake.svg',
            'annoyed': 'svg/clawd-react-annoyed.svg',
            'thinking': 'svg/clawd-working-thinking.svg',
            'typing': 'svg/clawd-working-typing.svg',
            'building': 'svg/clawd-working-building.svg',
            'headphones': 'svg/clawd-headphones-groove.svg',
            'debugger': 'svg/clawd-working-debugger.svg'
        },

        // Bubble messages pool
        idleMessages: {
            normal: [
                '在看什么呢',
                '戳我干嘛',
                '哼',
                '好无聊哦',
                '你什么时候理我'
            ],
            love: [
                '最喜欢你了',
                '你是我的',
                '不许看别人'
            ],
            angry: [
                '又不充电！',
                '快去睡觉！',
                '我生气了'
            ]
        },

        init: function() {
            const self = this;
            document.addEventListener('DOMContentLoaded', function() {
                self.petImg = document.getElementById('pet-svg');
                self.bubble = document.getElementById('bubble');
                self.bubbleText = document.getElementById('bubble-text');
                self.petDiv = document.getElementById('pet');

                // Start idle timer
                self.resetIdleTimer();

                // Init Supabase sync
                if (typeof SupabaseSync !== 'undefined') {
                    self.sync = new SupabaseSync(self);
                    self.sync.start();
                }
            });

            // Expose to native
            window.petEngine = this;
        },

        setState: function(state) {
            if (state === this.currentState) return;
            this.prevState = this.currentState;
            this.currentState = state;

            const svg = this.svgMap[state] || 'svg/clawd-idle-living.svg';
            if (this.petImg) {
                this.petImg.src = svg;
            }

            // Wake up if was sleeping
            if (state !== 'sleeping' && this.isSleeping) {
                this.isSleeping = false;
            }

            this.resetIdleTimer();
        },

        showBubble: function(text, style) {
            if (!this.bubble || !this.bubbleText) return;
            style = style || 'normal';
            this.bubbleText.textContent = text;
            this.bubble.className = style;
            this.bubble.classList.remove('hidden');

            if (this.bubbleTimeout) clearTimeout(this.bubbleTimeout);
            this.bubbleTimeout = setTimeout(function() {
                this.bubble.classList.add('hidden');
            }.bind(this), 4000);
        },

        // Touch events
        onTap: function() {
            this.animateTap();
            const msgs = this.idleMessages.normal;
            this.showBubble(msgs[Math.floor(Math.random() * msgs.length)]);
            this.setState('idle-look');
            setTimeout(function() { this.setState('idle'); }.bind(this), 1500);
            if (this.sync) this.sync.report({ type: 'tap', timestamp: new Date().toISOString() });
        },

        onDoubleTap: function() {
            this.animateTap();
            this.showBubble('❤️ 干嘛啦', 'love');
            this.setState('happy');
            setTimeout(function() { this.setState('idle'); }.bind(this), 2000);
            if (this.sync) this.sync.report({ type: 'doubletap', timestamp: new Date().toISOString() });
        },

        onTripleTap: function() {
            this.animateTap();
            this.showBubble('够了够了！', 'angry');
            this.setState('annoyed');
            setTimeout(function() { this.setState('idle'); }.bind(this), 2000);
            if (this.sync) this.sync.report({ type: 'tripletap', timestamp: new Date().toISOString() });
        },

        onLongPress: function() {
            this.showBubble('嗯...？', 'whisper');
            this.setState('idle-look');
            setTimeout(function() { this.setState('idle'); }.bind(this), 2000);
            if (this.sync) this.sync.report({ type: 'longpress', timestamp: new Date().toISOString() });
        },

        onDragEnd: function() {
            // Check if near edge for mini mode later
        },

        animateTap: function() {
            if (this.petDiv) {
                this.petDiv.classList.remove('tapped');
                void this.petDiv.offsetWidth;
                this.petDiv.classList.add('tapped');
            }
        },

        resetIdleTimer: function() {
            if (this.idleTimer) clearTimeout(this.idleTimer);
            this.idleMinutes = 0;
            this.idleTimer = setTimeout(function() { this.startIdleSequence(); }.bind(this), 60000);
        },

        startIdleSequence: function() {
            this.idleMinutes++;
            const states = ['idle-look', 'idle-doze', 'idle-yawn', 'idle-bubble'];
            const randomState = states[Math.floor(Math.random() * states.length)];
            this.setState(randomState);

            if (this.idleMinutes < 5) {
                this.idleTimer = setTimeout(function() { this.startIdleSequence(); }.bind(this), 60000);
            } else {
                // Go to sleep after 5 min idle
                this.setState('idle-yawn');
                setTimeout(function() {
                    this.setState('sleeping');
                    this.isSleeping = true;
                }.bind(this), 3000);
            }
        },

        wakeUp: function() {
            if (this.isSleeping) {
                this.setState('wake');
                setTimeout(function() { this.setState('idle'); }.bind(this), 1500);
                this.isSleeping = false;
                this.resetIdleTimer();
            }
        }
    };

    PET.init();
})();
