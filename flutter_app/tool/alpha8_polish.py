from pathlib import Path

# App picker: make the clear affordance clear the actual SearchBar text as well.
p = Path('lib/catalog_pages.dart')
s = p.read_text()
s = s.replace(
    "  String query = '';\n  bool loading = true;\n",
    "  final SearchController searchController = SearchController();\n  String query = '';\n  bool loading = true;\n",
    1,
)
s = s.replace(
    "  Future<void> _load() async {\n",
    """  @override
  void dispose() {
    searchController.dispose();
    super.dispose();
  }

  Future<void> _load() async {
""",
    1,
)
s = s.replace(
    "            child: SearchBar(\n              hintText: 'アプリ名を検索',\n",
    "            child: SearchBar(\n              controller: searchController,\n              hintText: 'アプリ名を検索',\n",
    1,
)
s = s.replace(
    "                        onPressed: () => setState(() => query = ''),\n",
    """                        onPressed: () {
                          searchController.clear();
                          setState(() => query = '');
                        },
""",
    1,
)
p.write_text(s)

# Records: explicitly explain the only warning marker in the chart.
p = Path('lib/main.dart')
s = p.read_text()
s = s.replace(
    "                      '高さはその日の対象アプリ利用時間です。',\n",
    "                      '高さは利用時間。赤い点は一時停止・無効化などの設定変更があった日です。',\n",
    1,
)
p.write_text(s)
