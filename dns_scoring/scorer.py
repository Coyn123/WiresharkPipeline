class Scorer:
    def __init__(self, nts):
        self.nts = nts
        self.total_domains = len(self.nts)
        self.trie = Trie()
        self.create_trie()


    def create_trie(self):
        for name in self.nts:
            name = name[0][::-1]
            Trie.insert(self.trie, name)
        self.score_names()
        return True

    def score_names(self):
        results = []
        for url in self.nts:
            name = url[0]
            reversed_url = name[::-1]

            node = self.trie.root
            depth = 0
            last_count = 0

            for c in reversed_url:
                if c not in node.children:
                    break
                node = node.children[c]
                last_count = node.count
                depth += 1

            total_len = len(reversed_url)
            depth_ratio = depth / total_len if total_len > 0 else 0
            freq_ratio = last_count / self.total_domains
            score = depth_ratio * freq_ratio

            results.append((score, name))

        results.sort(key=lambda x: x[0])
        for score, name in results[:25]:
            print(f"SCORE: {score:.6f} | {name}")

class TrieNode:
    def __init__(self):
        self.children = {}
        self.is_terminal = False
        self.count = 0

class Trie:
    def __init__(self):
        self.root = TrieNode()

    def insert(self, name):
        node = self.root
        for c in name:
            if c not in node.children:
                node.children[c] = TrieNode()
            node = node.children[c]
            node.count += 1
        node.is_terminal = True

    def search(self, char):
        node = self.root
        for c in char:
            if c not in node.children:
                return None
            node = node.children[c]
        return node