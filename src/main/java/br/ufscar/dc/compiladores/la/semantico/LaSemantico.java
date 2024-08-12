package br.ufscar.dc.compiladores.la.semantico;

import br.ufscar.dc.compiladores.la.semantico.TabelaDeSimbolos.TipoLa;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.antlr.v4.parse.ANTLRParser.elementOptions_return;

public class LaSemantico extends LaSemanticBaseVisitor<Void> {

    // Gerencia os escopos do programa
    public static Escopos escopos = new Escopos();
    private TipoLa tipoRetornoFuncaoAtual; // Adicionado para armazenar o tipo de retorno da função atual
    private Map<String, List<TipoLa>> parametrosFuncoes = new HashMap<>(); // Armazena os parâmetros das
                                                                           // funções/procedimentos
    private Map<String, TipoLa> tiposRetornoFuncoes = new HashMap<>(); // Armazena os tipos de retorno das funções
    public static Map<String, ArrayList<String>> tiposLista = new HashMap<>();

    @Override
    public Void visitPrograma(LaSemanticParser.ProgramaContext ctx) {
        escopos.criarNovoEscopo();
        return super.visitPrograma(ctx);
    }

    @Override
    public Void visitDeclaracao_local(LaSemanticParser.Declaracao_localContext ctx) {
        TabelaDeSimbolos tabelaLocal = escopos.obterEscopoAtual();

        if (ctx.variavel() != null) {
            if (ctx.variavel().tipo().registro() != null) {
                // Declaração de um registro
                for (LaSemanticParser.IdentificadorContext ic : ctx.variavel().identificador()) {
                    tabelaLocal.adicionar(ic.getText(), TipoLa.REG);
                    LaSemanticParser.RegistroContext registroCtx = ctx.variavel().tipo().registro();
                    for (LaSemanticParser.VariavelContext vc : registroCtx.variavel()) {
                        String tipoVariavelStr = vc.tipo().getText();
                        TipoLa tipoVariavelLa = obterTipo(tipoVariavelStr);

                        if (tipoVariavelLa != TipoLa.INVALIDO) {
                            for (LaSemanticParser.IdentificadorContext icr : vc.identificador()) {
                                String nomeCampo = icr.getText();
                                tabelaLocal.adicionar(ic.getText() + "." + nomeCampo, tipoVariavelLa);
                            }

                        }
                    }
                }
            } else {
                // Verifica se o tipo da variável está na lista de tipos definidos pelo usuário
                String tipoVar = ctx.variavel().tipo().getText();
                if (tiposLista.containsKey(tipoVar)) {
                    for (LaSemanticParser.IdentificadorContext ic : ctx.variavel().identificador()) {
                        String nomeRegistro = ic.IDENT().get(0).getText();
                        if (tabelaLocal.existe(nomeRegistro) || tiposLista.containsKey(nomeRegistro)) {
                            LaSemanticoUtils.adicionarErroSemantico(ic.getStart(),
                                    "identificador " + nomeRegistro + " ja declarado anteriormente");
                        } else {
                            List<String> variaveisTipo = tiposLista.get(tipoVar);
                            for (String nomeCampo : variaveisTipo) {
                                tabelaLocal.adicionar(nomeRegistro + "." + nomeCampo, TipoLa.VAR);
                            }
                            tabelaLocal.adicionar(nomeRegistro, TipoLa.REG);

                        }
                    }
                } else {
                    // Declaração de variáveis normais
                    for (LaSemanticParser.IdentificadorContext identificador : ctx.variavel().identificador()) {
                        String nomeVar = identificador.IDENT().get(0).getText();
                        TipoLa tipoVariavel = obterTipo(tipoVar);

                        if (tabelaLocal.existe(nomeVar)) {
                            LaSemanticoUtils.adicionarErroSemantico(identificador.getStart(),
                                    "identificador " + nomeVar + " ja declarado anteriormente");
                        } else if (tipoVariavel == TipoLa.INVALIDO) {
                            LaSemanticoUtils.adicionarErroSemantico(identificador.getStart(),
                                    "tipo " + tipoVar + " nao declarado");
                            tabelaLocal.adicionar(nomeVar, tipoVariavel);
                        } else {
                            tabelaLocal.adicionar(nomeVar, tipoVariavel);
                        }
                    }
                }
            }
        } else if (ctx.getText().contains("constante")) {
            // Declaração de constante
            tabelaLocal.adicionar(ctx.IDENT().getText(), TipoLa.CONSTANTE);
        } else if (ctx.tipo() != null) {
            // Declaração de tipos definidos pelo usuário
            String tipoName = ctx.IDENT().getText();
            if (ctx.tipo().registro() != null) {
                ArrayList<String> variaveisTipo = new ArrayList<>();
                for (LaSemanticParser.VariavelContext vc : ctx.tipo().registro().variavel()) {
                    for (LaSemanticParser.IdentificadorContext ic : vc.identificador()) {
                        variaveisTipo.add(ic.getText());
                    }
                }
                tiposLista.put(tipoName, variaveisTipo);
                tabelaLocal.adicionar(tipoName, TipoLa.REG);
            } else {
                LaSemanticoUtils.adicionarErroSemantico(ctx.getStart(), "É um tipo não suportado");
            }
        }

        return super.visitDeclaracao_local(ctx);
    }

    @Override
    public Void visitDeclaracao_global(LaSemanticParser.Declaracao_globalContext ctx) {
        TabelaDeSimbolos tabelaGlobal = escopos.obterEscopoAtual();
        String nome = ctx.IDENT().getText();

        // Verifica se o identificador já foi declarado
        if (tabelaGlobal.existe(nome)) {
            LaSemanticoUtils.adicionarErroSemantico(ctx.IDENT().getSymbol(),
                    "identificador " + nome + " ja declarado anteriormente");
        } else {
            // Define o tipo de retorno da função/procedimento
            TipoLa tipo = ctx.tipo_estendido() != null ? obterTipo(ctx.tipo_estendido().getText()) : TipoLa.INVALIDO;
            if (ctx.tipo_estendido() != null && tipo == TipoLa.INVALIDO
                    && !tiposLista.containsKey(ctx.tipo_estendido().getText())) {
                LaSemanticoUtils.adicionarErroSemantico(ctx.IDENT().getSymbol(),
                        "tipo " + ctx.tipo_estendido().getText() + " nao declarado");
            }

            // Adiciona a função ou procedimento na tabela global com o tipo de retorno
            tabelaGlobal.adicionar(nome, tipo);
            tiposRetornoFuncoes.put(nome, tipo); // Armazena o tipo de retorno

            // Cria novo escopo para os parâmetros da função ou procedimento
            escopos.criarNovoEscopo();
            List<TipoLa> parametros = new ArrayList<>();
            if (ctx.parametros() != null) {
                List<LaSemanticParser.ParametroContext> params = ctx.parametros().parametro();
                for (LaSemanticParser.ParametroContext param : params) {
                    for (LaSemanticParser.IdentificadorContext identificador : param.identificador()) {
                        String nomeParam = identificador.getText();
                        String tipoParam = param.tipo_estendido().getText();
                        TipoLa tipoParamLa = obterTipo(tipoParam);

                        // Verifica se o tipo do parâmetro é válido
                        if (tipoParamLa == TipoLa.INVALIDO && !tiposLista.containsKey(tipoParam)) {
                            LaSemanticoUtils.adicionarErroSemantico(identificador.getStart(),
                                    "tipo " + tipoParam + " nao declarado");
                        } else if (escopos.obterEscopoAtual().existe(nomeParam)) {
                            // Verifica se o identificador do parâmetro já foi declarado
                            LaSemanticoUtils.adicionarErroSemantico(identificador.getStart(),
                                    "identificador " + nomeParam + " ja declarado anteriormente");
                        } else {
                            if(tiposLista.containsKey(tipoParam)){
                                List<String> variaveisTipo = tiposLista.get(tipoParam);
                                for (String nomeCampo : variaveisTipo) {
                                    escopos.obterEscopoAtual().adicionar(nomeParam+"."+nomeCampo, TipoLa.VAR);
                                }
                                escopos.obterEscopoAtual().adicionar(nomeParam, TipoLa.REG);
                                parametros.add(TipoLa.REG);
                            }else{
                                escopos.obterEscopoAtual().adicionar(nomeParam, tipoParamLa);
                                parametros.add(tipoParamLa);
                            }                            // Adiciona o parâmetro na tabela de símbolos do escopo atual
                            
                        }
                    }
                }
            }
            // Armazena os parâmetros da função ou procedimento
            parametrosFuncoes.put(nome, parametros);

            // Armazena o tipo de retorno da função atual
            tipoRetornoFuncaoAtual = tipo;
            super.visitDeclaracao_global(ctx);
            // Volta ao escopo anterior ao finalizar a análise da função
            escopos.abandonarEscopo();
            tipoRetornoFuncaoAtual = null; // Reseta o tipo de retorno da função atual
        }
        return null;
    }

    @Override
    public Void visitCmdChamada(LaSemanticParser.CmdChamadaContext ctx) {
        String nome = ctx.IDENT().getText();
        TabelaDeSimbolos tabelaAtual = escopos.obterEscopoAtual();

        // Verifica se o identificador existe
        if (!tabelaAtual.existe(nome)) {
            LaSemanticoUtils.adicionarErroSemantico(ctx.IDENT().getSymbol(),
                    "identificador " + nome + " nao declarado");
            return null;
        }

        // Recupera o tipo e parâmetros da função ou procedimento
        TipoLa tipoChamada = tabelaAtual.verificar(nome);
        List<TipoLa> parametrosChamada = parametrosFuncoes.get(nome);

        // Verifica se a função ou procedimento possui parâmetros definidos
        if (parametrosChamada == null) {
            LaSemanticoUtils.adicionarErroSemantico(ctx.IDENT().getSymbol(),
                    "funcao ou procedimento " + nome + " nao definido");
            return null;
        }

        // Verifica a quantidade de parâmetros
        List<LaSemanticParser.ExpressaoContext> parametrosPassados = ctx.expressao();
        if (parametrosChamada.size() != parametrosPassados.size()) {
            LaSemanticoUtils.adicionarErroSemantico(ctx.IDENT().getSymbol(),
                    "incompatibilidade de parametros na chamada de " + nome);
            return null;
        }

        // Verifica os tipos de cada parâmetro
        for (int i = 0; i < parametrosPassados.size(); i++) {
            LaSemanticParser.ExpressaoContext parametroPassadoCtx = parametrosPassados.get(i);
            TipoLa tipoParametroPassado = LaSemanticoUtils.verificarTipo(escopos.obterEscopoAtual(),
                    parametroPassadoCtx);
            TipoLa tipoParametroEsperado = parametrosChamada.get(i);

            if (tipoParametroEsperado != tipoParametroPassado
                    && !tiposLista.containsKey(tipoParametroEsperado.toString())) {
                LaSemanticoUtils.adicionarErroSemantico(ctx.IDENT().getSymbol(),
                        "incompatibilidade de parametros na chamada de " + nome);
            }
        }

        return null;
    }

    @Override
    public Void visitCmdRetorne(LaSemanticParser.CmdRetorneContext ctx) {
        TipoLa tipoRetornoExpressao = LaSemanticoUtils.verificarTipo(escopos.obterEscopoAtual(), ctx.expressao());

        // Verifica se estamos dentro de uma função
        if (tipoRetornoFuncaoAtual == null || !tipoCompativel(tipoRetornoFuncaoAtual, tipoRetornoExpressao)) {
            LaSemanticoUtils.adicionarErroSemantico(ctx.getStart(),
                    "comando retorne nao permitido nesse escopo");
        } /* else {
            // Verifica se o tipo de retorno da expressão é compatível com o tipo de retorno
            // da função
            TipoLa tipoRetornoExpressao = LaSemanticoUtils.verificarTipo(escopos.obterEscopoAtual(), ctx.expressao());
            if (!tipoCompativel(tipoRetornoFuncaoAtual, tipoRetornoExpressao)) {
                LaSemanticoUtils.adicionarErroSemantico(ctx.expressao().start,
                        "Tipo de retorno incompatível: esperado " + tipoRetornoFuncaoAtual + " mas encontrado "
                                + tipoRetornoExpressao);
            }
        } */
        return super.visitCmdRetorne(ctx);
    }

    @Override
    public Void visitCmdAtribuicao(LaSemanticParser.CmdAtribuicaoContext ctx) {
        // Verifica o tipo da expressão
        TipoLa tipoExpressao = LaSemanticoUtils.verificarTipo(escopos.obterEscopoAtual(), ctx.expressao());
        String nomeVar = ctx.identificador().getText();
        TabelaDeSimbolos tabelaAtual = escopos.obterEscopoAtual();
        if (ctx.identificador().dimensao().getText().length() > 0) {
            nomeVar = ctx.identificador().IDENT().get(0).getText();
        }
        // Verifica se a variável foi declarada
        if (!tabelaAtual.existe(nomeVar)) {
            LaSemanticoUtils.adicionarErroSemantico(ctx.identificador().getStart(),
                    "identificador " + nomeVar + " nao declarado");
        } else {
            TipoLa tipoVariavel = tabelaAtual.verificar(nomeVar);

            // Verifica se a variável é um campo de um registro
            if (tipoVariavel == TipoLa.REG) {
                // O identificador pode ser um campo de um registro
                // O identificador completo inclui o nome do registro e o campo
                // Verifica se é um campo de um registro
                if (nomeVar.contains(".")) {
                    // Obtém o nome do registro e o campo
                    String[] partes = nomeVar.split("\\.");
                    String nomeRegistro = partes[0];
                    String nomeCampo = partes[1];

                    // Verifica se o campo existe no registro
                    TipoLa tipoCampo = tabelaAtual.verificar(nomeRegistro + "." + nomeCampo);

                    if (tipoCampo == TipoLa.INVALIDO) {
                        LaSemanticoUtils.adicionarErroSemantico(ctx.identificador().getStart(),
                                "campo " + nomeCampo + " no registro " + nomeRegistro + " nao declarado");
                    } else {
                        tipoVariavel = tipoCampo;
                    }
                }
            }

            // Verifica se o tipo da variável é compatíve l com o tipo da expressão
            if (!tipoCompativel(tipoVariavel, tipoExpressao) && tipoVariavel != TipoLa.VAR && tipoExpressao != TipoLa.VAR) {
                if (tipoVariavel == TipoLa.PONTEIROINT) {
                    LaSemanticoUtils.adicionarErroSemantico(ctx.identificador().getStart(),
                            "atribuicao nao compativel para ^" + nomeVar);
                } else {
                    LaSemanticoUtils.adicionarErroSemantico(ctx.identificador().getStart(),
                            "atribuicao nao compativel para " + ctx.identificador().getText());
                }
            }
        }

        return super.visitCmdAtribuicao(ctx);
    }

    @Override
    public Void visitCmdEnquanto(LaSemanticParser.CmdEnquantoContext ctx) {
        TabelaDeSimbolos tabelaEnquanto = escopos.obterEscopoAtual();

        // Verifica se a expressão no comando 'enquanto' é do tipo lógico
        TipoLa tipo = LaSemanticoUtils.verificarTipo(tabelaEnquanto,
                ctx.expressao());
        if (tipo != TipoLa.LOG) {
            LaSemanticoUtils.adicionarErroSemantico(ctx.expressao().start,
                    "Expressão no comando 'enquanto' não é do tipo lógico");
        }

        return super.visitCmdEnquanto(ctx);
    }

    @Override
    public Void visitCmdEscreva(LaSemanticParser.CmdEscrevaContext ctx) {
        TabelaDeSimbolos tabelaEscreva = escopos.obterEscopoAtual();

        // Verifica o tipo das expressões nos comandos 'escreva'
        for (LaSemanticParser.ExpressaoContext expressao : ctx.expressao()) {
            TipoLa tipo = LaSemanticoUtils.verificarTipo(tabelaEscreva,
                    expressao);
        }

        return super.visitCmdEscreva(ctx);
    }

    @Override
    public Void visitCmdLeia(LaSemanticParser.CmdLeiaContext ctx) {
        List<LaSemanticParser.IdentificadorContext> identificadores = ctx.identificador();
        for (LaSemanticParser.IdentificadorContext identificador : identificadores) {
            String nomeVar = identificador.getText();
            if (identificador.dimensao().getText().length() > 0) {
                nomeVar = identificador.IDENT().get(0).getText();
            }

            if (!escopos.obterEscopoAtual().existe(nomeVar)) {
                LaSemanticoUtils.adicionarErroSemantico(identificador.getStart(),
                        "identificador " + identificador.getText() + " nao declarado");
            }

        }
        return super.visitCmdLeia(ctx);
    }

    private TipoLa obterTipo(String strTipo) {
        // Verifica se o tipo é um tipo definido pelo usuário
        if (tiposLista.containsKey(strTipo)) {
            return TipoLa.REG;
        }
        // Adiciona todos os tipos possíveis em minúsculas
        switch (strTipo.toLowerCase()) {
            case "inteiro":
                return TipoLa.INT;
            case "real":
                return TipoLa.REAL;
            case "literal":
                return TipoLa.LIT;
            case "logico":
                return TipoLa.LOG;
            case "^inteiro":
                return TipoLa.PONTEIROINT;
            case "^real":
                return TipoLa.PONTEIROREAL;
            case "funcao":
                return TipoLa.FUNCAO;
            case "procedimento":
                return TipoLa.PROCEDIMENTO;
            case "constante":
                return TipoLa.CONSTANTE;
            default:
                return TipoLa.INVALIDO;
        }
    }
    

    public static boolean tipoCompativel(TipoLa tipoVar, TipoLa tipoExpr) {
        if (tipoVar == tipoExpr) {
            return true;
        }
        // Inteiros podem ser atribuídos a reais e vice-versa
        if ((tipoVar == TipoLa.REAL && tipoExpr == TipoLa.INT) ||
                (tipoVar == TipoLa.INT && tipoExpr == TipoLa.REAL)) {
            return true;
        }
        // Adiciona compatibilidade entre ENDERECO e PONTEIRO
        if ((tipoVar == TipoLa.PONTEIROINT && tipoExpr == TipoLa.ENDERECO) ||
                (tipoVar == TipoLa.ENDERECO && tipoExpr == TipoLa.PONTEIROINT)) {
            return true;
        }
        if ((tipoVar == TipoLa.REAL && tipoExpr == TipoLa.VAR) ||
                (tipoVar == TipoLa.VAR && tipoExpr == TipoLa.REAL)) {
            return true;
        }
        if ((tipoVar == TipoLa.VAR && tipoExpr == TipoLa.INT) ||
                (tipoVar == TipoLa.INT && tipoExpr == TipoLa.VAR)) {
            return true;
        }
        return false;
    }
}
