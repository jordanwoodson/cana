<?php
// Render the same merged package records in ADL's browser and Cana's JSON feed.
// Run upstream's baseline tests first; its documented fixed-count assertions do
// not describe this larger locally reviewed dataset. The browser generator then
// validates these converted records using upstream's strict reference schema.
declare(strict_types=1);

$root = $argv[1] ?? throw new RuntimeException('Missing ADL root');
$mergedFile = $argv[2] ?? throw new RuntimeException('Missing merged UAD list');
$reviewFile = $argv[3] ?? throw new RuntimeException('Missing review ledger');
$read = fn(string $file): array => json_decode(file_get_contents($file), true, 512, JSON_THROW_ON_ERROR);
$lists = ['Aosp' => 'aosp', 'Carrier' => 'carrier', 'Google' => 'google', 'Misc' => 'misc', 'Oem' => 'oem'];
$removals = ['Recommended' => 'delete', 'Advanced' => 'replace', 'Expert' => 'caution', 'Unsafe' => 'unsafe'];
$original = [];
foreach ($lists as $name) foreach ($read("$root/$name.json") as $item) $original[$item['id']] = $item;
$merged = $read($mergedFile);
$reviews = $read($reviewFile)['packages'] ?? throw new RuntimeException('Invalid review ledger');
$categories = array_fill_keys(array_values($lists), []);
foreach ($merged as $id => $entry) {
    $category = $lists[$entry['list'] ?? ''] ?? throw new RuntimeException("Invalid list: $id");
    $removal = $removals[$entry['removal'] ?? ''] ?? throw new RuntimeException("Invalid removal: $id");
    $description = $entry['description'] ?? throw new RuntimeException("Missing description: $id");
    $record = ['id' => $id, 'description' => $description, 'removal' => $removal,
               'dependencies' => $entry['dependencies'] ?? [], 'required_by' => $entry['neededBy'] ?? [], 'tags' => []];
    if (isset($original[$id]['label'])) $record['label'] = $original[$id]['label'];
    if (isset($original[$id]['web'])) $record['web'] = $original[$id]['web'];
    if (isset($reviews[$id])) {
        $review = $reviews[$id];
        // A current APK label corrects outdated labels for reused package IDs.
        if (!empty($review['display_label']) && $review['display_label'] !== $id) $record['label'] = $review['display_label'];
        elseif (!empty($review['apk_labels'])) $record['label'] = $review['apk_labels'][0];
        $record['description'] .= "\n\nAssociated features: " . implode(', ', $review['features']) . '.';
        $record['description'] .= "\nReview confidence: " . $review['confidence'] . '.';
        $record['web'] = array_values(array_unique(array_column(array_filter($review['sources'],
            fn(array $source): bool => in_array($source['kind'], ['primary', 'community'], true)
                && preg_match('#^https?://#', $source['url']) === 1), 'url')));
    }
    if (!empty($entry['source'])) $record['description'] .= "\nList source: " . $entry['source'] . '.';
    // Suggestions in merged UAD files are display names, not ADL category IDs.
    if (!empty($entry['suggestions'])) $record['description'] .= "\nRecorded replacement suggestions: " . $entry['suggestions'] . '.';
    $categories[$category][] = $record;
}
foreach ($reviews as $id => $review) {
    if (!isset($merged[$id])) throw new RuntimeException("Reviewed package missing from merged list: $id");
}
foreach ($categories as $name => $records) {
    file_put_contents("$root/$name.json", json_encode($records, JSON_PRETTY_PRINT | JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES | JSON_THROW_ON_ERROR) . "\n");
}
// Replace the upstream frozen-data paragraph so the overview does not contradict
// the locally reviewed entries displayed by the regenerated browser.
$overview = "$root/browser/README.md";
if (is_file($overview)) {
    $text = file_get_contents($overview);
    $text = preg_replace('/The package data was last changed in this fork on \*\*[^*]+\*\*\. v\{\{VERSION\}\} updates the reference presentation and packaging, not the underlying recommendations\./',
        'This local reference merges ADL, supplements and UAD-NG with the individually reviewed Motorola Edge 2025 audit of October 1, 2026. Reviewed descriptions and recommendations take precedence; each revised entry identifies its review source and confidence.', $text);
    file_put_contents($overview, $text);
}
echo count($merged) . " merged package records prepared for the browser, including " . count($reviews) . " reviewed records.\n";
