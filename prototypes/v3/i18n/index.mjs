/** Labels are supplied as a zh/en pair; business notes are never translated. */
export function localizer(getLanguage){return (zh,en)=>getLanguage()==='en'?en:zh;}
