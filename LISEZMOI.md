# Comment utiliser ces outils pour la rédaction d'articles pour Developpez.com ? 

Le format de base de travail pour QtDocTools est DocBook, un vocabulaire XML utilisé 
pour la rédaction et disposant d'un relativement bon écosystème. Parmi les meilleurs
éditeurs, on peut sûrement recommander [Oxygen](https://www.oxygenxml.com/) (payant) 
et [XMLmind](https://www.xmlmind.com/xmleditor/) (l'édition gratuite est suffisante).

QtDocTools dispose aussi d'un système d'importation de fichiers DOCX, ce qui permet
de travailler dans Word, avec les styles, puis d'importer le document en DocBook. 
Par ailleurs, pour la gestion des documents anciens, il est aussi possible d'importer
des documents DvpML. 

Pour travailler dans d'autres formats et toujours bénéficier de QtDocTools, 
[pandoc](https://pandoc.org/) peut se révéler utile. 

## Relectures orthographiques

QtDocTools dispose également d'une fonctionnalité d'export en DOCX pour la gestion
des relectures orthographiques : le rédacteur exporte son document en DOCX, l'envoie
au correcteur, en accepte les modifications, puis importe le document corrigé. Un 
maximum d'informations sémantiques sur l'article sera gardé. 

En théorie, ce fichier DOCX peut être édité de n'importe quelle manière, tant que les 
styles sont préservés. En particulier, ce n'est pas le cas de LibreOffice 6.4, qui 
perd énormément d'informations sur les styles lors du chargement du document. 

À terme, un module permettra de récupérer les informations manquantes depuis le 
fichier exporté depuis le document DOCX. 

## Gestion des auteurs

Les auteurs sont censés être renseignés dans chaque document, au niveau de la base
`info`. `author` sert à désigner les auteurs, `othercredit` les autres contributions. 
Les rôles suivants sont implémentés : 

* `proofreader` : correcteur ; 
* `conversion` : mise au gabarit ; 
* `reviewer` : relecteur technique ; 
* `translator` : traducteur.

Le nom est censé indiqué par la balise `personname`, le pseudo par 
`othername role="pseudonym"`. Le lien vers le profil forum doit être indiqué dans 
la balise `uri role="main-uri"`.

## Mise en ligne

Chaque document DocBook est accompagné d'un petit fichier JSON qui donne toutes les 
métadonnées qui n'appartiennent pas au document DocBook, notamment celles nécessaires
pour la mise en ligne (gestion du FTP). Ce fichier est censé porter le même nom que le 
document DocBook, à l'extension près : si le document s'appelle `doc.xml`, son fichier
de configuration doit être `doc.json`.
 
Voici un exemple de contenu pour ce fichier de configuration : 

```json
{
	"section": 1,
	"license-author": "",
	"license-year": "2020",
	"license-number": 1,
	"license-text": "",
	"forum-topic": -1,
	"forum-post": -1,
	"ftp-server": "",
	"ftp-user": "",
	"ftp-port": "",
	"ftp-folder": "",
	"google-analytics": ""
}
```

Le mot de passe FTP n'est jamais stocké par QtDocTools. Il fait appel au système 
de gestion de mots de passe du système d'exploitation (KWallet, GNOME Keyring, 
Keychain, Windows Credential Vault) pour une gestion sécurisée des mots de passe. 
La première fois, le mot de passe est demandé.

La configuration des articles se fait de manière hiérarchique : un document hérite 
des propriétés des fichiers de configuration des dossiers le contenant. À la racine
du dépôt de documents, le fichier `root.json` contient des propriétés partagées par 
tous les documents contenus dans ce dossier (notamment, les informations FTP ou le 
compte Google Analytics). Ensuite, un dossier peut contenir un fichier `related.json`,
qui sert à gérer les articles référencés en bas de page (voir section correspondante). 

## Gestion des références

Le fichier de références doit au moins spécifier un dossier de stockage en ligne, puis
une série de références. Celles-ci sont organisées par section, chaque section ayant
un titre et une liste de références (c'est-à-dire une URL et un texte à afficher). 
L'URL est donnée comme un chemin relatif vers l'article en question (l'URL en ligne
sera reconstruite automatiquement). 

Voici un exemple de tel fichier : 

```{
	"ftp-folder": "tutoriels/julia",
	"related-documents": {
		"Julia": {
			"Julia, une introduction, mais vite": "introduction-rapide-1.1/julia.xml",
			"Graphes et Julia": "introduction-lightgraphs-1.2/julia-graphes.xml",
			"Optimisation mathématique en Julia avec JuMP 0.21": "introduction-jump-0.21/julia-jump.xml",
			"Optimisation mathématique en Julia avec JuMP 0.20 (obsolète)": "introduction-jump-0.20/julia-jump.xml"
		}
	}
}```