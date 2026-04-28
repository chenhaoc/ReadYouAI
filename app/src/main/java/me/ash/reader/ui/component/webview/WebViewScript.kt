package me.ash.reader.ui.component.webview

object WebViewScript {

    fun get(
        boldCharacters: Boolean,
        normalizeDarkTextColors: Boolean,
    ) = """
const BR_WORD_STEM_PERCENTAGE = 0.7;
const MAX_FIXATION_PARTS = 4;
const FIXATION_LOWER_BOUND = 0
function highlightText(sentenceText) {
	return sentenceText.replace(/\p{L}+/gu, (word) => {
		const { length } = word;

		const brWordStemWidth = length > 3 ? Math.round(length * BR_WORD_STEM_PERCENTAGE) : length;

		const firstHalf = word.slice(0, brWordStemWidth);
		const secondHalf = word.slice(brWordStemWidth);
		var htmlWord = "<br-bold>";
        htmlWord += makeFixations(firstHalf);
        htmlWord += "</br-bold>";
        if (secondHalf.length) {
            htmlWord += "<br-edge>";
            htmlWord += makeFixations(secondHalf);
            htmlWord += "</br-edge>";
        }
		return htmlWord;
	});
}

function makeFixations(textContent) {
	const COMPUTED_MAX_FIXATION_PARTS = textContent.length >= MAX_FIXATION_PARTS ? MAX_FIXATION_PARTS : textContent.length;

	const fixationWidth = Math.ceil(textContent.length * (1 / COMPUTED_MAX_FIXATION_PARTS));

	if (fixationWidth === FIXATION_LOWER_BOUND) {
		return '<br-fixation fixation-strength="1">' + textContent + '</br-fixation>';
	}

	const fixationsSplits = new Array(COMPUTED_MAX_FIXATION_PARTS).fill(null).map((item, index) => {
		const wordStartBoundary = index * fixationWidth;
		const wordEndBoundary = wordStartBoundary + fixationWidth > textContent.length ? textContent.length : wordStartBoundary + fixationWidth;

		return `<br-fixation fixation-strength="` + (index + 1) + `">` + textContent.slice(wordStartBoundary, wordEndBoundary) + `</br-fixation>`;
	});

	return fixationsSplits.join('');
}

const IGNORE_NODE_TAGS = ['STYLE', 'SCRIPT', 'BR-SPAN', 'BR-FIXATION', 'BR-BOLD', 'BR-EDGE', 'SVG', 'INPUT', 'TEXTAREA'];
function parseNode(node) {
    if (!node?.parentElement?.tagName || IGNORE_NODE_TAGS.includes(node.parentElement.tagName)) {
        return;
    }
    
    if (node.nodeType === Node.TEXT_NODE && node.nodeValue.length) {
        try {
            const brSpan = document.createElement('br-span');
            brSpan.innerHTML = highlightText(node.nodeValue);
            if (brSpan.childElementCount === 0) return;
            node.parentElement.replaceChild(brSpan, node); // JiffyReader keeps the old element around, but we don't need it
        } catch (e) {
            console.error('Error parsing text node:', e);
        }
        return;
    }
    
    if (node.hasChildNodes()) [...node.childNodes].forEach(parseNode);
}

function setBold(enabled) {
    if (enabled) {
        document.body.setAttribute("br-mode", "on");
        [...document.body.childNodes].forEach(parseNode);
    } else {
        document.body.setAttribute("br-mode", "off");
    }
}

function parseComputedColor(color) {
    const match = color?.match(/rgba?\((\d+),\s*(\d+),\s*(\d+)/i);
    if (!match) return null;
    return {
        r: Number(match[1]),
        g: Number(match[2]),
        b: Number(match[3]),
    };
}

function luminanceChannel(value) {
    const normalized = value / 255;
    if (normalized <= 0.03928) return normalized / 12.92;
    return Math.pow((normalized + 0.055) / 1.055, 2.4);
}

function isDarkForeground(color) {
    const rgb = parseComputedColor(color);
    if (!rgb) return false;
    const luminance =
        0.2126 * luminanceChannel(rgb.r) +
        0.7152 * luminanceChannel(rgb.g) +
        0.0722 * luminanceChannel(rgb.b);
    return luminance < 0.2;
}

function normalizeDarkTextColors() {
    const selectors = ['[style*="color"]', 'font[color]', '[color]'].join(',');
    document.querySelectorAll(selectors).forEach((element) => {
        if (!(element instanceof HTMLElement)) return;
        if (element.closest('a, pre, code, svg, img, video, iframe, object, embed, button, input, textarea')) {
            return;
        }
        const computedColor = window.getComputedStyle(element).color;
        if (!isDarkForeground(computedColor)) return;
        const useBoldColor =
            element.closest('h1, h2, h3, h4, h5, h6, strong, b') !== null;
        element.style.setProperty(
            'color',
            useBoldColor ? 'var(--bold-text-color)' : 'var(--text-color)',
            'important'
        );
        if (element.hasAttribute('color')) {
            element.removeAttribute('color');
        }
    });
}

${if (boldCharacters) "setBold(true);" else ""}
${if (normalizeDarkTextColors) "normalizeDarkTextColors();" else ""}

var images = document.querySelectorAll("img");

images.forEach(function(img) {
    img.onload = function() {
        img.classList.add("loaded");
        console.log("Image width:", img.width, "px");
        if (img.width < 412) {
            img.classList.add("thin");
        }
    };

    img.onerror = function() {
        console.error("Failed to load image:", img.src);
    };
});

function reportContentHeight() {
    if (!window.${JavaScriptInterface.NAME}) return;
    const article = document.querySelector('article');
    const main = document.querySelector('main');
    const body = document.body;
    const html = document.documentElement;
    const measureElementHeight = (element) => {
        if (!element) return 0;
        const rectHeight = element.getBoundingClientRect ? element.getBoundingClientRect().height : 0;
        return Math.max(
            rectHeight || 0,
            element.scrollHeight || 0,
            element.offsetHeight || 0,
            element.clientHeight || 0
        );
    };
    const height = Math.max(
        measureElementHeight(article),
        measureElementHeight(main),
        measureElementHeight(body),
        measureElementHeight(html)
    );
    window.${JavaScriptInterface.NAME}.onContentHeightChanged(Math.ceil(height));
}

let scheduledHeightReport = false;
function scheduleContentHeightReport() {
    if (scheduledHeightReport) return;
    scheduledHeightReport = true;
    requestAnimationFrame(() => {
        scheduledHeightReport = false;
        reportContentHeight();
    });
}

reportContentHeight();
scheduleContentHeightReport();
window.addEventListener('load', scheduleContentHeightReport);
window.addEventListener('resize', scheduleContentHeightReport);
document.addEventListener('readystatechange', scheduleContentHeightReport);
setTimeout(scheduleContentHeightReport, 50);
setTimeout(scheduleContentHeightReport, 150);
setTimeout(scheduleContentHeightReport, 500);
setTimeout(scheduleContentHeightReport, 1000);
if (window.ResizeObserver) {
    const resizeObserver = new ResizeObserver(scheduleContentHeightReport);
    if (document.body) {
        resizeObserver.observe(document.body);
    }
    if (document.documentElement) {
        resizeObserver.observe(document.documentElement);
    }
    const article = document.querySelector('article');
    if (article) {
        resizeObserver.observe(article);
    }
    const main = document.querySelector('main');
    if (main) {
        resizeObserver.observe(main);
    }
}
if (window.MutationObserver && document.body) {
    new MutationObserver(scheduleContentHeightReport).observe(document.body, {
        childList: true,
        subtree: true,
        characterData: true,
        attributes: true,
    });
}
"""
}
