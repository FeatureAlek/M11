const api = {
    list: () => fetch('/api/chats').then(r => r.json()),
    create: () => fetch('/api/chats', { method: 'POST' }).then(r => r.json()),
    get: (id) => fetch(`/api/chats/${id}`).then(r => r.json()),
    remove: (id) => fetch(`/api/chats/${id}`, { method: 'DELETE' }),
    send: (id, content) => fetch(`/api/chats/${id}/chatMessages`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ content })
    }).then(async r => {
        if (!r.ok) {
            const problem = await r.json().catch(() => ({}));
            throw new Error(problem.detail || 'Request failed');
        }
        return r.json();
    }),
    uploadDocument: (id, file) => {
        const formData = new FormData();
        formData.append('file', file);
        return fetch(`/api/chats/${id}/documents`, {
            method: 'POST',
            body: formData
        }).then(async r => {
            if (!r.ok) {
                const problem = await r.json().catch(() => ({}));
                throw new Error(problem.detail || 'Upload failed');
            }
            return r.json();
        });
    }
};

const els = {
    chatList: document.getElementById('chat-list'),
    messages: document.getElementById('chatMessages'),
    title: document.getElementById('chat-title'),
    input: document.getElementById('input'),
    send: document.getElementById('send'),
    form: document.getElementById('composer'),
    error: document.getElementById('error'),
    newChat: document.getElementById('new-chat'),
    uploadBtn: document.getElementById('upload'),
    fileInput: document.getElementById('file-input')
};

let activeChatId = null;

function setError(message) {
    els.error.textContent = message || '';
}

function setComposerEnabled(enabled) {
    els.input.disabled = !enabled;
    els.send.disabled = !enabled;
    els.uploadBtn.disabled = !enabled;
}

async function refreshChatList() {
    const chats = await api.list();
    els.chatList.innerHTML = '';
    chats.forEach(chat => els.chatList.appendChild(renderChatItem(chat)));
}

function renderChatItem(chat) {
    const item = document.createElement('div');
    item.className = 'chat-item' + (chat.id === activeChatId ? ' active' : '');
    item.onclick = () => openChat(chat.id);

    const title = document.createElement('span');
    title.className = 'title';
    title.textContent = chat.title;

    const count = document.createElement('span');
    count.className = 'count';
    count.textContent = chat.messageCount;

    const del = document.createElement('button');
    del.className = 'del';
    del.textContent = '🗑';
    del.onclick = (e) => { e.stopPropagation(); deleteChat(chat.id); };

    item.append(title, count, del);
    return item;
}

function renderMessages(chat) {
    els.title.textContent = chat.title;
    els.messages.innerHTML = '';
    if (!chat.chatMessages.length) {
        els.messages.innerHTML = '<div class="empty">Say hello to start the conversation.</div>';
        return;
    }
    chat.chatMessages.forEach(m => els.messages.appendChild(renderMessage(m)));
    els.messages.scrollTop = els.messages.scrollHeight;
}

function renderMessage(message) {
    const wrapper = document.createElement('div');
    wrapper.className = `msg ${message.role.toLowerCase()}`;
    const role = document.createElement('div');
    role.className = 'role';
    role.textContent = message.role;
    const body = document.createElement('div');
    body.textContent = message.content;
    wrapper.append(role, body);
    return wrapper;
}

async function openChat(id) {
    setError('');
    activeChatId = id;
    const chat = await api.get(id);
    renderMessages(chat);
    setComposerEnabled(true);
    els.input.focus();
    await refreshChatList();
}

async function startNewChat() {
    setError('');
    const chat = await api.create();
    await refreshChatList();
    await openChat(chat.id);
}

async function deleteChat(id) {
    setError('');
    await api.remove(id);
    if (id === activeChatId) {
        activeChatId = null;
        els.title.textContent = 'Select or start a chat';
        els.messages.innerHTML = '<div class="empty">No conversation selected.</div>';
        setComposerEnabled(false);
    }
    await refreshChatList();
}

async function submitMessage(content) {
    setError('');
    setComposerEnabled(false);
    appendPending(content);
    try {
        const chat = await api.send(activeChatId, content);
        renderMessages(chat);
        await refreshChatList();
    } catch (err) {
        setError(err.message);
        const chat = await api.get(activeChatId);
        renderMessages(chat);
    } finally {
        setComposerEnabled(true);
        els.input.focus();
    }
}

function appendPending(content) {
    els.messages.querySelector('.empty')?.remove();
    els.messages.appendChild(renderMessage({ role: 'USER', content }));
    const thinking = renderMessage({ role: 'ASSISTANT', content: '…' });
    thinking.classList.add('pending');
    els.messages.appendChild(thinking);
    els.messages.scrollTop = els.messages.scrollHeight;
}

els.newChat.onclick = startNewChat;

els.form.addEventListener('submit', (e) => {
    e.preventDefault();
    const content = els.input.value.trim();
    if (!content || !activeChatId) return;
    els.input.value = '';
    submitMessage(content);
});

els.input.addEventListener('keydown', (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault();
        els.form.requestSubmit();
    }
});

function appendSystemNote(text) {
    els.messages.querySelector('.empty')?.remove();
    const note = document.createElement('div');
    note.className = 'msg assistant';
    note.style.opacity = '0.7';
    note.style.fontStyle = 'italic';
    note.textContent = text;
    els.messages.appendChild(note);
    els.messages.scrollTop = els.messages.scrollHeight;
}

async function uploadFile(file) {
    setError('');
    setComposerEnabled(false);
    appendSystemNote(`Uploading "${file.name}"...`);
    try {
        const doc = await api.uploadDocument(activeChatId, file);
        appendSystemNote(`✅ "${doc.filename}" uploaded and indexed (${doc.chunkCount} chunks). Ask me about it!`);
    } catch (err) {
        appendSystemNote(`❌ Failed to upload "${file.name}": ${err.message}`);
    } finally {
        setComposerEnabled(true);
        els.fileInput.value = '';
    }
}

els.uploadBtn.onclick = () => els.fileInput.click();

els.fileInput.addEventListener('change', () => {
    const file = els.fileInput.files[0];
    if (file && activeChatId) {
        uploadFile(file);
    }
});

refreshChatList();
