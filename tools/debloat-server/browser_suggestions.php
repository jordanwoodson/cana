<?php
// Upstream's browser generator omits its replacement lists. Append the original suggestions
// after page generation, preserving every upstream description, warning and removal rating.
declare(strict_types=1);

$root = $argv[1] ?? throw new RuntimeException('Missing Android Debloat List root');
function escape(string $text): string { return htmlspecialchars($text, ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8'); }

foreach (['aosp', 'carrier', 'google', 'misc', 'oem'] as $category) {
    $records = json_decode(file_get_contents("$root/$category.json"), true, 512, JSON_THROW_ON_ERROR);
    foreach ($records as $record) {
        if (empty($record['suggestions'])) continue;
        $apps = json_decode(file_get_contents("$root/suggestions/{$record['suggestions']}.json"), true, 512, JSON_THROW_ON_ERROR);
        if (!$apps) continue;
        $content = "\n\n## Suggested replacements\n\n<ul>\n";
        foreach ($apps as $app) {
            $label = escape($app['label']);
            $repo = $app['repo'];
            $link = in_array(parse_url($repo, PHP_URL_SCHEME), ['https', 'http'], true)
                ? '<a href="' . escape($repo) . '">' . $label . '</a>' : $label;
            $content .= '<li>' . $link . ' (<code>' . escape($app['id']) . '</code>)';
            if (!empty($app['reason'])) $content .= '<p>' . nl2br(escape($app['reason'])) . '</p>';
            $content .= "</li>\n";
        }
        $content .= "</ul>\n";
        $page = "$root/browser/src/bloatware/{$record['id']}.md";
        if (!is_file($page)) throw new RuntimeException("Missing upstream page: $page");
        if (file_put_contents($page, $content, FILE_APPEND) !== strlen($content)) {
            throw new RuntimeException("Cannot append replacements to $page");
        }
    }
}
