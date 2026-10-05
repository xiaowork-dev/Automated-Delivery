let draft = ''
export const setRedeemDraft = code => { draft = code }
export const takeRedeemDraft = () => { const code = draft; draft = ''; return code }
